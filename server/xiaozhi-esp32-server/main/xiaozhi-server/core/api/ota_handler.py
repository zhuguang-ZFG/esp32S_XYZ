import json
import time
import base64
import hashlib
import hmac
import os
import re
import glob
from typing import Dict, List, Tuple
from aiohttp import web
import httpx

from core.auth import AuthManager
from core.utils.util import get_local_ip, get_vision_url
from core.api.base_handler import BaseHandler

TAG = __name__


def _safe_basename(filename: str) -> str:
    # Prevent directory traversal
    return os.path.basename(filename)


def _parse_version(ver: str) -> Tuple[int, ...]:
    # conservative parser: split by non-digit, keep numeric parts
    parts = re.findall(r"\d+", ver)
    return tuple(int(p) for p in parts) if parts else (0,)


def _is_higher_version(a: str, b: str) -> bool:
    """Return True if version string a > b (semver-like numeric compare)."""
    ta = _parse_version(a)
    tb = _parse_version(b)
    # compare tuple lexicographically, but allow different lengths
    maxlen = max(len(ta), len(tb))
    for i in range(maxlen):
        ai = ta[i] if i < len(ta) else 0
        bi = tb[i] if i < len(tb) else 0
        if ai > bi:
            return True
        if ai < bi:
            return False
    return False


def _is_https_url(url: str) -> bool:
    return url.startswith("https://")


def _is_lower_hex_sha256(value: str) -> bool:
    return bool(re.fullmatch(r"[0-9a-f]{64}", value or ""))


def _is_base64_signature(value: str) -> bool:
    if not value:
        return False
    try:
        return bool(base64.b64decode(value, validate=True))
    except Exception:
        # Not a valid base64 string — expected for non-signature values
        return False


def _business_result_error(payload: dict | None) -> str:
    if not isinstance(payload, dict) or "code" not in payload:
        return ""
    if str(payload.get("code")) == "0":
        return ""
    return str(payload.get("msg") or payload.get("message") or "business result error")


def _header_value(headers, names: Tuple[str, ...]) -> str:
    for name in names:
        if name in headers:
            return str(headers.get(name, "") or "").strip()
    return ""


def _extract_device_model(headers, body: dict) -> str:
    model = _header_value(headers, ("device-model", "device_model", "model"))
    if model:
        return model
    board = body.get("board") if isinstance(body, dict) else None
    if isinstance(board, dict):
        model = str(board.get("type") or "").strip()
    elif isinstance(body, dict):
        model = str(body.get("model") or "").strip()
    return model or "default"


def _extract_device_version(headers, body: dict) -> str:
    version = _header_value(
        headers,
        (
            "device-version",
            "device_version",
            "firmware-version",
            "app-version",
            "application-version",
        ),
    )
    if version:
        return version
    application = body.get("application") if isinstance(body, dict) else None
    if isinstance(application, dict):
        version = str(application.get("version") or "").strip()
    return version or "0.0.0"


def _extract_firmware_channel(headers, body: dict) -> str:
    channel = _header_value(headers, ("firmware-channel", "ota-channel"))
    if channel:
        return channel
    if isinstance(body, dict):
        channel = str(body.get("firmware_channel") or body.get("channel") or "").strip()
    return channel or "dev"


def _base_ota_response(server_config: dict, device_version: str) -> dict:
    return {
        "server_time": {
            "timestamp": int(round(time.time() * 1000)),
            "timezone_offset": server_config.get("timezone_offset", 8) * 60,
        },
        "firmware": {
            "version": device_version,
            "url": "",
        },
    }


def _select_local_firmware(
    candidates: List[Tuple[str, str]],
    device_version: str,
    config: dict,
) -> Tuple[str, str]:
    for ver, fname in candidates:
        if _is_higher_version(ver, device_version):
            vision_url = get_vision_url(config)
            return (
                ver,
                vision_url.replace(
                    "/mcp/vision/explain", f"/xiaozhi/ota/download/{fname}"
                ),
            )
    return device_version, ""


class OTAHandler(BaseHandler):
    def __init__(self, config: dict):
        super().__init__(config)
        auth_config = config["server"].get("auth", {})
        self.auth_enable = auth_config.get("enabled", False)
        # 设备白名单
        self.allowed_devices = set(auth_config.get("allowed_devices", []))
        secret_key = config["server"]["auth_key"]
        expire_seconds = auth_config.get("expire_seconds")
        self.auth = AuthManager(secret_key=secret_key, expire_seconds=expire_seconds)

        # firmware storage
        self.bin_dir = os.path.join(os.getcwd(), "data", "bin")
        # cache structure: { 'updated_at': timestamp, 'ttl': seconds, 'files_by_model': { model: [(version, filename), ...] } }
        self._bin_cache: Dict = {
            "updated_at": 0,
            "ttl": config.get("firmware_cache_ttl", 30),
            "files_by_model": {},
        }

    def _refresh_bin_cache_if_needed(self):
        now = int(time.time())
        ttl = int(self._bin_cache.get("ttl", 30))
        if now - int(
            self._bin_cache.get("updated_at", 0)
        ) < ttl and self._bin_cache.get("files_by_model"):
            return

        files_by_model: Dict[str, List[Tuple[str, str]]] = {}
        try:
            if not os.path.isdir(self.bin_dir):
                os.makedirs(self.bin_dir, exist_ok=True)

            # match files like model_1.2.3.bin (allow dots, dashes, underscores in model and version)
            pattern = os.path.join(self.bin_dir, "*.bin")
            for path in glob.glob(pattern):
                fname = os.path.basename(path)
                # filename format: {model}_{version}.bin
                m = re.match(r"^(.+?)_([0-9][A-Za-z0-9\.\-_]*)\.bin$", fname)
                if not m:
                    # skip files not conforming to naming rule
                    continue
                model = m.group(1)
                version = m.group(2)
                files_by_model.setdefault(model, []).append((version, fname))

            # sort versions for each model descending
            for model, items in files_by_model.items():
                items.sort(key=lambda it: _parse_version(it[0]), reverse=True)

            self._bin_cache["files_by_model"] = files_by_model
            self._bin_cache["updated_at"] = now
            self.logger.bind(tag=TAG).info(
                f"Firmware cache refreshed: {len(files_by_model)} models"
            )
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"刷新固件缓存失败: {e}")
            # keep previous cache if any

    def generate_password_signature(self, content: str, secret_key: str) -> str:
        """生成MQTT密码签名

        Args:
            content: 签名内容 (clientId + '|' + username)
            secret_key: 密钥

        Returns:
            str: Base64编码的HMAC-SHA256签名
        """
        try:
            hmac_obj = hmac.new(
                secret_key.encode("utf-8"), content.encode("utf-8"), hashlib.sha256
            )
            signature = hmac_obj.digest()
            return base64.b64encode(signature).decode("utf-8")
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"生成MQTT密码签名失败: {e}")
            return ""

    def _get_websocket_url(self, local_ip: str, port: int) -> str:
        """获取websocket地址

        Args:
            local_ip: 本地IP地址
            port: 端口号

        Returns:
            str: websocket地址
        """
        server_config = self.config["server"]
        websocket_config = server_config.get("websocket", "")

        if "你的" not in websocket_config:
            return websocket_config
        else:
            return f"ws://{local_ip}:{port}/xiaozhi/v1/"

    def _business_release_base_url(self) -> str:
        server_config = self.config.get("server") or {}
        if not isinstance(server_config, dict):
            return ""
        return (
            server_config.get("firmware_release_business_base_url")
            or server_config.get("motion_event_business_base_url")
            or server_config.get("voice_task_business_base_url")
            or ""
        ).strip().rstrip("/")

    def _internal_token(self) -> str:
        server_config = self.config.get("server") or {}
        if not isinstance(server_config, dict):
            return ""
        return (server_config.get("internal_motion_task_token") or "").strip()

    async def _fetch_business_firmware_plan(
        self,
        device_id: str,
        channel: str,
        current_version: str,
    ) -> dict | None:
        base_url = self._business_release_base_url()
        token = self._internal_token()
        if not device_id or not base_url or not token:
            return None

        payload = {
            "device_id": device_id,
            "channel": channel or "dev",
            "current_version": current_version or "",
        }
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                resp = await client.post(
                    f"{base_url}/internal/v1/firmware/upgrade-plan",
                    json=payload,
                    headers={"Authorization": f"Bearer {token}"},
                )
            if resp.status_code >= 400:
                self.logger.bind(tag=TAG).warning(
                    "Business firmware plan failed HTTP {} body={}",
                    resp.status_code,
                    resp.text[:500],
                )
                return None
            response_payload = resp.json()
        except (httpx.HTTPError, OSError, ValueError) as e:
            self.logger.bind(tag=TAG).warning("Business firmware plan error: {}", e)
            return None

        if not isinstance(response_payload, dict):
            return None
        error = _business_result_error(response_payload)
        if error:
            self.logger.bind(tag=TAG).warning("Business firmware plan rejected: {}", error)
            return None
        plan = response_payload.get("data")
        return plan if isinstance(plan, dict) else None

    def _apply_business_firmware_plan(self, return_json: dict, plan: dict | None) -> bool:
        if not plan:
            return False
        version = str(plan.get("version") or "").strip()
        url = str(plan.get("url") or "").strip()
        sha256 = str(plan.get("sha256") or "").strip()
        signature = str(plan.get("signature") or "").strip()
        release_id = str(plan.get("releaseId") or plan.get("release_id") or "").strip()
        if not version or not url or not sha256 or not signature:
            return False
        if (
            not _is_https_url(url)
            or not _is_lower_hex_sha256(sha256)
            or not _is_base64_signature(signature)
        ):
            return False
        return_json["firmware"] = {
            "version": version,
            "url": url,
            "sha256": sha256,
            "signature": signature,
        }
        if release_id:
            return_json["firmware"]["release_id"] = release_id
        return True

    async def _forward_business_install_result(self, payload: dict) -> bool:
        base_url = self._business_release_base_url()
        token = self._internal_token()
        if not base_url or not token:
            return False
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                resp = await client.post(
                    f"{base_url}/internal/v1/firmware/install-result",
                    json=payload,
                    headers={"Authorization": f"Bearer {token}"},
                )
            if resp.status_code >= 400:
                self.logger.bind(tag=TAG).warning(
                    "Business firmware install result failed HTTP {} body={}",
                    resp.status_code,
                    resp.text[:500],
                )
                return False
            try:
                response_payload = resp.json()
            except ValueError:
                response_payload = None
            error = _business_result_error(response_payload)
            if error:
                self.logger.bind(tag=TAG).warning(
                    "Business firmware install result rejected: {}",
                    error,
                )
                return False
            return True
        except (httpx.HTTPError, OSError) as e:
            self.logger.bind(tag=TAG).warning("Business firmware install result error: {}", e)
            return False

    async def handle_install_result(self, request):
        try:
            body = await request.json()
        except Exception:
            response = web.json_response({"ok": False, "error": "invalid json"}, status=400)
            self._add_cors_headers(response)
            return response

        device_id = str(body.get("device_id") or request.headers.get("device-id") or "").strip()
        release_id = str(body.get("release_id") or body.get("releaseId") or "").strip()
        if not device_id or not release_id or "success" not in body:
            response = web.json_response(
                {"ok": False, "error": "device_id, release_id and success required"},
                status=400,
            )
            self._add_cors_headers(response)
            return response

        payload = {
            "device_id": device_id,
            "release_id": release_id,
            "success": bool(body.get("success")),
        }
        if body.get("reason"):
            payload["reason"] = str(body.get("reason"))
        forwarded = await self._forward_business_install_result(payload)
        response = web.json_response({"ok": forwarded, "forwarded": forwarded}, status=200 if forwarded else 503)
        self._add_cors_headers(response)
        return response

    async def handle_post(self, request):
        """处理 OTA POST 请求

        This handler will:
        - read device id/client id (as before)
        - attempt to determine device model and current firmware version (prefer headers, fallback to body)
        - check data/bin for newer firmware for that model
        - if found a newer firmware, set firmware.url to the download endpoint
        """
        try:
            data = await request.text()
            self.logger.bind(tag=TAG).debug(f"OTA请求方法: {request.method}")
            self.logger.bind(tag=TAG).debug(f"OTA请求头: {request.headers}")
            self.logger.bind(tag=TAG).debug(f"OTA请求数据: {data}")

            device_id = request.headers.get("device-id", "")
            if device_id:
                self.logger.bind(tag=TAG).info(f"OTA请求设备ID: {device_id}")
            else:
                raise Exception("OTA请求设备ID为空")

            client_id = request.headers.get("client-id", "")
            if client_id:
                self.logger.bind(tag=TAG).info(f"OTA请求ClientID: {client_id}")
            else:
                raise Exception("OTA请求ClientID为空")

            data_json = {}
            try:
                data_json = json.loads(data) if data else {}
            except Exception:
                data_json = {}

            server_config = self.config["server"]
            # Distinguish ports:
            # - websocket_port is used to construct websocket URL (server["port"])
            # - http_port is used to construct OTA download URLs (server["http_port"])
            websocket_port = int(server_config.get("port", 8000))
            http_port = int(server_config.get("http_port", 8003))
            local_ip = get_local_ip()

            device_model = _extract_device_model(request.headers, data_json)
            device_version = _extract_device_version(request.headers, data_json)
            firmware_channel = _extract_firmware_channel(request.headers, data_json)
            return_json = _base_ota_response(server_config, device_version)

            # existing mqtt/websocket logic (unchanged)
            mqtt_gateway_endpoint = server_config.get("mqtt_gateway")

            if mqtt_gateway_endpoint:  # 如果配置了非空字符串
                # 尝试从请求数据中获取设备型号（已解析 above）
                try:
                    group_id = f"GID_{device_model}".replace(":", "_").replace(" ", "_")
                except Exception as e:
                    self.logger.bind(tag=TAG).error(f"获取设备型号失败: {e}")
                    group_id = "GID_default"

                mac_address_safe = device_id.replace(":", "_")
                mqtt_client_id = f"{group_id}@@@{mac_address_safe}@@@{mac_address_safe}"

                # 构建用户数据
                user_data = {"ip": "unknown"}
                try:
                    user_data_json = json.dumps(user_data)
                    username = base64.b64encode(user_data_json.encode("utf-8")).decode(
                        "utf-8"
                    )
                except Exception as e:
                    self.logger.bind(tag=TAG).error(f"生成用户名失败: {e}")
                    username = ""

                # 生成密码
                password = ""
                signature_key = server_config.get("mqtt_signature_key", "")
                if signature_key:
                    password = self.generate_password_signature(
                        mqtt_client_id + "|" + username, signature_key
                    )
                    if not password:
                        password = ""  # 签名失败则留空，由设备决定是否允许无密码
                else:
                    self.logger.bind(tag=TAG).warning("缺少MQTT签名密钥，密码留空")

                # 构建MQTT配置（直接使用 mqtt_gateway 字符串）
                return_json["mqtt"] = {
                    "endpoint": mqtt_gateway_endpoint,
                    "client_id": mqtt_client_id,
                    "username": username,
                    "password": password,
                    "publish_topic": "device-server",
                    "subscribe_topic": f"devices/p2p/{mac_address_safe}",
                }
                self.logger.bind(tag=TAG).info(f"为设备 {device_id} 下发MQTT网关配置")

            else:  # 未配置 mqtt_gateway，下发 WebSocket
                # 如果开启了认证，则进行认证校验
                token = ""
                if self.auth_enable:
                    if self.allowed_devices:
                        if device_id not in self.allowed_devices:
                            token = self.auth.generate_token(client_id, device_id)
                    else:
                        token = self.auth.generate_token(client_id, device_id)
                # NOTE: use websocket_port here
                return_json["websocket"] = {
                    "url": self._get_websocket_url(local_ip, websocket_port),
                    "token": token,
                }
                self.logger.bind(tag=TAG).info(
                    f"未配置MQTT网关，为设备 {device_id} 下发WebSocket配置"
                )

            try:
                business_plan = await self._fetch_business_firmware_plan(
                    device_id,
                    firmware_channel,
                    device_version,
                )
                if self._apply_business_firmware_plan(return_json, business_plan):
                    self.logger.bind(tag=TAG).info(
                        f"Business firmware plan selected for device {device_id}: {return_json['firmware']['version']}"
                    )
                    response = web.Response(
                        text=json.dumps(return_json, separators=(",", ":")),
                        content_type="application/json",
                    )
                    return response
            except Exception as e:
                self.logger.bind(tag=TAG).error(f"Business firmware plan apply error: {e}")

            # Now check firmware files for updates
            try:
                self._refresh_bin_cache_if_needed()
                files_by_model = self._bin_cache.get("files_by_model", {})
                candidates = files_by_model.get(device_model, [])

                self.logger.bind(tag=TAG).info(
                    f"查找型号 {device_model} 的固件，找到 {len(candidates)} 个候选"
                )

                chosen_version, chosen_url = _select_local_firmware(
                    candidates,
                    device_version,
                    self.config,
                )

                if chosen_url:
                    return_json["firmware"]["version"] = chosen_version
                    return_json["firmware"]["url"] = chosen_url
                    self.logger.bind(tag=TAG).info(
                        f"为设备 {device_id} 下发固件 {chosen_version} [如果地址前缀有误，请检查配置文件中的server.vision_explain]-> {chosen_url} "
                    )
                else:
                    self.logger.bind(tag=TAG).info(
                        f"设备 {device_id} 固件已是最新: {device_version}"
                    )

            except Exception as e:
                self.logger.bind(tag=TAG).error(f"检查固件版本时出错: {e}")

            response = web.Response(
                text=json.dumps(return_json, separators=(",", ":")),
                content_type="application/json",
            )
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"OTA POST处理异常: {e}")
            return_json = {"success": False, "message": "request error."}
            response = web.Response(
                text=json.dumps(return_json, separators=(",", ":")),
                content_type="application/json",
            )
        finally:
            self._add_cors_headers(response)
            return response

    async def handle_get(self, request):
        """处理 OTA GET 请求"""
        try:
            server_config = self.config["server"]
            local_ip = get_local_ip()
            # use websocket port for websocket URL
            websocket_port = int(server_config.get("port", 8000))
            websocket_url = self._get_websocket_url(local_ip, websocket_port)
            message = f"OTA接口运行正常，向设备发送的websocket地址是：{websocket_url}"
            response = web.Response(text=message, content_type="text/plain")
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"OTA GET请求异常: {e}")
            response = web.Response(text="OTA接口异常", content_type="text/plain")
        finally:
            self._add_cors_headers(response)
            return response

    async def handle_download(self, request):
        """
        下载固件接口
        URL: /xiaozhi/ota/download/{filename}
        - 只允许下载 data/bin 目录下的 .bin 文件
        - filename 必须是 basename 且匹配安全的模式
        """
        try:
            fname = request.match_info.get("filename", "")
            if not fname:
                raise web.HTTPBadRequest(text="filename required")

            # sanitize
            fname = _safe_basename(fname)
            # pattern: allow letters, numbers, dot, underscore, dash
            if not re.match(r"^[A-Za-z0-9\.\-_]+\.bin$", fname):
                raise web.HTTPBadRequest(text="invalid filename")

            file_path = os.path.join(self.bin_dir, fname)
            # ensure realpath is under bin_dir
            file_real = os.path.realpath(file_path)
            bin_dir_real = os.path.realpath(self.bin_dir)
            if (
                not file_real.startswith(bin_dir_real + os.sep)
                and file_real != bin_dir_real
            ):
                raise web.HTTPForbidden(text="forbidden")

            if not os.path.isfile(file_real):
                raise web.HTTPNotFound(text="file not found")

            # use FileResponse to stream file
            resp = web.FileResponse(path=file_real)
        except web.HTTPError as e:
            resp = e
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"固件下载异常: {e}")
            resp = web.Response(text="download error", status=500)
        finally:
            try:
                self._add_cors_headers(resp)
            except Exception:
                pass
            return resp
