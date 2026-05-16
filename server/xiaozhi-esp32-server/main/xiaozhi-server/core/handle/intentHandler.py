import json
import uuid
import asyncio
import httpx
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from core.connection import ConnectionHandler
from core.utils.dialogue import Message
from core.providers.tts.dto.dto import ContentType
from core.handle.helloHandle import checkWakeupWords
from core.handle.deviceIntentMap import resolve_direct_device_tool, resolve_voice_task_intent
from plugins_func.register import Action, ActionResponse
from core.handle.sendAudioHandle import send_stt_message
from core.handle.reportHandle import enqueue_tool_report
from core.utils.util import remove_punctuation_and_length
from core.utils.voiceprint_cache import (
    decide_voiceprint_policy,
    refresh_voiceprint_cache_from_business,
    voiceprint_mode_from_config,
)
from core.providers.tts.dto.dto import TTSMessageDTO, SentenceType

TAG = __name__


async def handle_user_intent(conn: "ConnectionHandler", text):
    speaker_ref = None
    # 棰勫鐞嗚緭鍏ユ枃鏈紝澶勭悊鍙兘鐨凧SON鏍煎紡
    try:
        if text.strip().startswith("{") and text.strip().endswith("}"):
            parsed_data = json.loads(text)
            if isinstance(parsed_data, dict) and "content" in parsed_data:
                speaker_ref = parsed_data.get("speaker_ref") or parsed_data.get("speakerRef")
                text = parsed_data["content"]  # 鎻愬彇content鐢ㄤ簬鎰忓浘鍒嗘瀽
                conn.current_speaker = parsed_data.get("speaker")  # 淇濈暀璇磋瘽浜轰俊鎭?
    except (json.JSONDecodeError, TypeError):
        pass

    conn.current_member_id = None
    conn.current_voiceprint_member_type = None
    conn.current_voiceprint_reason = None
    conn.voiceprint_reenroll_hint = False
    conn.current_speaker_ref = speaker_ref
    if await reject_by_voiceprint_policy(conn, speaker_ref, text):
        return True

    # 妫€鏌ユ槸鍚︽湁鏄庣‘鐨勯€€鍑哄懡浠?
    _, filtered_text = remove_punctuation_and_length(text)
    if await check_direct_exit(conn, filtered_text):
        return True

    if await checkWakeupWords(conn, filtered_text):
        return True

    direct_device_intent = resolve_direct_device_tool(
        filtered_text,
        lambda tool_name: hasattr(conn, "func_handler")
        and conn.func_handler is not None
        and conn.func_handler.has_tool(tool_name),
    )
    if direct_device_intent:
        conn.logger.bind(tag=TAG).info(
            "璇嗗埆鍒扮‘瀹氭€ц澶囪闊虫剰鍥? {} -> {}",
            filtered_text,
            direct_device_intent["function_call"]["name"],
        )
        conn.sentence_id = str(uuid.uuid4().hex)
        return await process_intent_result(
            conn,
            json.dumps(direct_device_intent, ensure_ascii=False),
            text,
        )

    voice_task_intent = resolve_voice_task_intent(filtered_text)
    if voice_task_intent:
        conn.logger.bind(tag=TAG).info(
            "voice task intent {} -> {}",
            filtered_text,
            voice_task_intent["task"]["capability"],
        )
        conn.sentence_id = str(uuid.uuid4().hex)
        submitted = await submit_voice_task(conn, voice_task_intent["task"], text)
        if submitted:
            await send_stt_message(conn, text)
            return True

    if conn.intent_type == "function_call":
        # 浣跨敤鏀寔function calling鐨勮亰澶╂柟娉?涓嶅啀杩涜鎰忓浘鍒嗘瀽
        return False
    # 浣跨敤LLM杩涜鎰忓浘鍒嗘瀽
    intent_result = await analyze_intent_with_llm(conn, text)
    if not intent_result:
        return False
    # 浼氳瘽寮€濮嬫椂鐢熸垚sentence_id
    conn.sentence_id = str(uuid.uuid4().hex)
    # 澶勭悊鍚勭鎰忓浘
    return await process_intent_result(conn, intent_result, text)


async def reject_by_voiceprint_policy(conn: "ConnectionHandler", speaker_ref, original_text: str) -> bool:
    mode = voiceprint_mode_from_config(conn.config)
    if mode == "voiceprint_off":
        return False

    device_id = str(getattr(conn, "device_id", "") or "").strip()
    if not device_id:
        conn.logger.bind(tag=TAG).warning("voiceprint policy skipped because device_id is missing")
        return False

    cache = getattr(conn, "voiceprint_cache", None)
    if cache is None:
        return False

    voiceprint_cfg = conn.config.get("voiceprint") or {}
    if not isinstance(voiceprint_cfg, dict):
        voiceprint_cfg = {}
    ttl_seconds = int(voiceprint_cfg.get("cache_ttl_seconds") or 300)
    try:
        if not cache.is_fresh(device_id, ttl_seconds):
            await refresh_voiceprint_cache_from_business(
                conn.config,
                device_id,
                cache,
                conn.logger.bind(tag=TAG),
            )
    except ValueError:
        return False

    entries = cache.entries_for_device(device_id)
    decision = decide_voiceprint_policy(mode, entries, speaker_ref)
    if decision["allowed"]:
        conn.current_voiceprint_reason = decision["reason"]
        member = decision.get("member")
        if member is not None:
            conn.current_member_id = member.member_id
            conn.current_speaker = member.display_name
            conn.current_voiceprint_member_type = member.member_type
        if decision["reason"] == "child_reenroll_required":
            conn.voiceprint_reenroll_hint = True
        return False

    conn.logger.bind(tag=TAG).warning(
        "voiceprint rejected device_id={} mode={} reason={}",
        device_id,
        mode,
        decision["reason"],
    )
    conn.sentence_id = str(uuid.uuid4().hex)
    await send_stt_message(conn, original_text)
    speak_txt(conn, "\u672a\u901a\u8fc7\u58f0\u7eb9\u9a8c\u8bc1\uff0c\u8bf7\u8ba9\u5df2\u767b\u8bb0\u7684\u5bb6\u5ead\u6210\u5458\u91cd\u8bd5\u3002")
    return True


async def submit_voice_task(conn: "ConnectionHandler", task: dict, original_text: str) -> bool:
    server_cfg = conn.config.get("server") or {}
    if not isinstance(server_cfg, dict):
        return False
    device_id = str(getattr(conn, "device_id", "") or "").strip()
    base = (
        server_cfg.get("voice_task_business_base_url")
        or server_cfg.get("motion_event_business_base_url")
        or ""
    ).strip().rstrip("/")
    token = (server_cfg.get("internal_motion_task_token") or "").strip()
    if not device_id or not base or not token:
        conn.logger.bind(tag=TAG).warning(
            "voice task submit skipped device_id={} base_configured={} token_configured={}",
            bool(device_id),
            bool(base),
            bool(token),
        )
        return False

    capability = str(task.get("capability") or "").strip()
    request_id = "voice-" + str(uuid.uuid4().hex)
    payload = {
        "device_id": device_id,
        "capability": capability,
        "source": "voice",
        "request_id": request_id,
        "trace_id": request_id,
        "params": task.get("params") or {},
    }
    voiceprint_constraints = build_voiceprint_constraints(conn)
    if voiceprint_constraints:
        payload["constraints"] = {"voiceprint": voiceprint_constraints}
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.post(
                f"{base}/internal/v1/voice_task",
                json=payload,
                headers={"Authorization": f"Bearer {token}"},
            )
        if resp.status_code >= 400:
            conn.logger.bind(tag=TAG).warning(
                "voice task submit failed HTTP {} body={}",
                resp.status_code,
                resp.text[:500],
            )
            speak_txt(conn, "\u8bed\u97f3\u4efb\u52a1\u63d0\u4ea4\u5931\u8d25\u3002")
            return True
        response_payload = _response_json(resp)
        business_error = _business_result_error(response_payload)
        if business_error:
            conn.logger.bind(tag=TAG).warning(
                "voice task submit rejected by business server: {}",
                business_error,
            )
            speak_txt(conn, "\u8bed\u97f3\u4efb\u52a1\u63d0\u4ea4\u5931\u8d25\u3002")
            return True
        conn.dialogue.put(Message(role="user", content=original_text))
        speak_txt(conn, voice_task_confirmation_text(conn, response_payload))
        return True
    except (httpx.HTTPError, OSError) as e:
        conn.logger.bind(tag=TAG).warning("voice task submit error: {}", e)
        speak_txt(conn, "\u8bed\u97f3\u4efb\u52a1\u63d0\u4ea4\u5931\u8d25\u3002")
        return True


def build_voiceprint_constraints(conn: "ConnectionHandler") -> dict:
    member_id = getattr(conn, "current_member_id", None)
    speaker_ref = getattr(conn, "current_speaker_ref", None)
    reason = getattr(conn, "current_voiceprint_reason", None)
    if member_id is None and not reason:
        return {}

    constraints = {
        "matched": member_id is not None,
        "reason": reason or "unknown",
    }
    if member_id is not None:
        constraints["member_id"] = member_id
    display_name = getattr(conn, "current_speaker", None)
    if display_name:
        constraints["display_name"] = display_name
    member_type = getattr(conn, "current_voiceprint_member_type", None)
    if member_type:
        constraints["member_type"] = member_type
    if speaker_ref:
        constraints["speaker_ref"] = speaker_ref
    if getattr(conn, "voiceprint_reenroll_hint", False):
        constraints["reenroll_hint"] = True
    return constraints


def voice_task_confirmation_text(conn: "ConnectionHandler", response_payload: dict | None = None) -> str:
    approval_required = _nested_value(response_payload, "data", "approvalRequiredBy")
    reason = getattr(conn, "current_voiceprint_reason", None)
    reenroll_hint = getattr(conn, "voiceprint_reenroll_hint", False)
    if reason == "child_reenroll_required" or reenroll_hint:
        if approval_required == "primary":
            return "\u4efb\u52a1\u5df2\u63d0\u4ea4\uff0c\u7b49\u5f85\u5bb6\u957f\u786e\u8ba4\u3002\u8fd9\u4e2a\u5b69\u5b50\u7684\u58f0\u7eb9\u9700\u8981\u91cd\u65b0\u5f55\u5165\u3002"
        return "\u4efb\u52a1\u5df2\u63d0\u4ea4\u3002\u8fd9\u4e2a\u5b69\u5b50\u7684\u58f0\u7eb9\u9700\u8981\u91cd\u65b0\u5f55\u5165\u3002"
    if approval_required == "primary":
        if reason in ("child_unknown_allowed", "unknown_allowed"):
            return "\u4efb\u52a1\u5df2\u63d0\u4ea4\uff0c\u672a\u5339\u914d\u5230\u767b\u8bb0\u58f0\u7eb9\uff0c\u7b49\u5f85\u5bb6\u957f\u786e\u8ba4\u3002"
        return "\u4efb\u52a1\u5df2\u63d0\u4ea4\uff0c\u7b49\u5f85\u5bb6\u957f\u786e\u8ba4\u3002"
    return "\u4efb\u52a1\u5df2\u63d0\u4ea4\u3002"


def _response_json(response) -> dict | None:
    try:
        payload = response.json()
    except (ValueError, AttributeError):
        return None
    return payload if isinstance(payload, dict) else None


def _business_result_error(payload: dict | None) -> str:
    if not isinstance(payload, dict) or "code" not in payload:
        return ""
    if str(payload.get("code")) == "0":
        return ""
    return str(payload.get("msg") or payload.get("message") or "business result error")


def _nested_value(payload: dict | None, *keys):
    current = payload
    for key in keys:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


async def check_direct_exit(conn: "ConnectionHandler", text):
    """Check explicit exit command."""
    _, text = remove_punctuation_and_length(text)
    cmd_exit = conn.cmd_exit
    for cmd in cmd_exit:
        if text == cmd:
            conn.logger.bind(tag=TAG).info(f"璇嗗埆鍒版槑纭殑閫€鍑哄懡浠? {text}")
            await send_stt_message(conn, text)
            await conn.close()
            return True
    return False


async def analyze_intent_with_llm(conn: "ConnectionHandler", text):
    """浣跨敤LLM鍒嗘瀽鐢ㄦ埛鎰忓浘"""
    if not hasattr(conn, "intent") or not conn.intent:
        conn.logger.bind(tag=TAG).warning("鎰忓浘璇嗗埆鏈嶅姟鏈垵濮嬪寲")
        return None

    # 瀵硅瘽鍘嗗彶璁板綍
    dialogue = conn.dialogue
    try:
        intent_result = await conn.intent.detect_intent(conn, dialogue.dialogue, text)
        return intent_result
    except Exception as e:
        conn.logger.bind(tag=TAG).error(f"鎰忓浘璇嗗埆澶辫触: {str(e)}")

    return None


async def process_intent_result(
    conn: "ConnectionHandler", intent_result, original_text
):
    """澶勭悊鎰忓浘璇嗗埆缁撴灉"""
    try:
        # 灏濊瘯灏嗙粨鏋滆В鏋愪负JSON
        intent_data = json.loads(intent_result)

        # 妫€鏌ユ槸鍚︽湁function_call
        if "function_call" in intent_data:
            # 鐩存帴浠庢剰鍥捐瘑鍒幏鍙栦簡function_call
            conn.logger.bind(tag=TAG).debug(
                f"妫€娴嬪埌function_call鏍煎紡鐨勬剰鍥剧粨鏋? {intent_data['function_call']['name']}"
            )
            function_name = intent_data["function_call"]["name"]
            if function_name == "continue_chat":
                return False

            if function_name == "result_for_context":
                await send_stt_message(conn, original_text)
                conn.client_abort = False

                def process_context_result():
                    conn.dialogue.put(Message(role="user", content=original_text))

                    from core.utils.current_time import get_current_time_info

                    current_time, today_date, today_weekday, lunar_date = (
                        get_current_time_info()
                    )

                    # 鏋勫缓甯︿笂涓嬫枃鐨勫熀纭€鎻愮ず
                    context_prompt = (
                        f"current_time: {current_time}\n"
                        f"today_date: {today_date} ({today_weekday})\n"
                        f"lunar_date: {lunar_date}\n"
                        f"user_text: {original_text}"
                    )

                    response = conn.intent.replyResult(context_prompt, original_text)
                    speak_txt(conn, response)

                conn.executor.submit(process_context_result)
                return True

            function_args = {}
            if "arguments" in intent_data["function_call"]:
                function_args = intent_data["function_call"]["arguments"]
                if function_args is None:
                    function_args = {}
            # 纭繚鍙傛暟鏄瓧绗︿覆鏍煎紡鐨凧SON
            if isinstance(function_args, dict):
                function_args = json.dumps(function_args)

            function_call_data = {
                "name": function_name,
                "id": str(uuid.uuid4().hex),
                "arguments": function_args,
            }

            await send_stt_message(conn, original_text)
            conn.client_abort = False

            # 鍑嗗宸ュ叿璋冪敤鍙傛暟
            tool_input = {}
            if function_args:
                if isinstance(function_args, str):
                    tool_input = json.loads(function_args) if function_args else {}
                elif isinstance(function_args, dict):
                    tool_input = function_args

            # 涓婃姤宸ュ叿璋冪敤
            enqueue_tool_report(conn, function_name, tool_input)

            # 浣跨敤executor鎵ц鍑芥暟璋冪敤鍜岀粨鏋滃鐞?
            def process_function_call():
                conn.dialogue.put(Message(role="user", content=original_text))
                
                # 宸ュ叿璋冪敤瓒呮椂鏃堕棿
                tool_call_timeout = int(conn.config.get("tool_call_timeout", 30))
                # 浣跨敤缁熶竴宸ュ叿澶勭悊鍣ㄥ鐞嗘墍鏈夊伐鍏疯皟鐢?
                try:
                    result = asyncio.run_coroutine_threadsafe(
                        conn.func_handler.handle_llm_function_call(
                            conn, function_call_data
                        ),
                        conn.loop,
                    ).result(timeout=tool_call_timeout)
                except Exception as e:
                    conn.logger.bind(tag=TAG).error(f"宸ュ叿璋冪敤澶辫触: {e}")
                    result = ActionResponse(
                        action=Action.ERROR,
                        result="tool call timeout",
                        response="tool call timeout",
                    )

                # 涓婃姤宸ュ叿璋冪敤缁撴灉
                if result:
                    enqueue_tool_report(conn, function_name, tool_input, str(result.result) if result.result else None, report_tool_call=False)

                    if result.action == Action.RESPONSE:  # 鐩存帴鍥炲鍓嶇
                        text = result.response
                        if text is not None:
                            speak_txt(conn, text)
                    elif result.action == Action.REQLLM:  # 璋冪敤鍑芥暟鍚庡啀璇锋眰llm鐢熸垚鍥炲
                        text = result.result
                        conn.dialogue.put(Message(role="tool", content=text))
                        llm_result = conn.intent.replyResult(text, original_text)
                        if llm_result is None:
                            llm_result = text
                        speak_txt(conn, llm_result)
                    elif (
                        result.action == Action.NOTFOUND
                        or result.action == Action.ERROR
                    ):
                        text = result.response if result.response else result.result
                        if text is not None:
                            speak_txt(conn, text)
                    elif function_name != "play_music":
                        # For backward compatibility with original code
                        # 鑾峰彇褰撳墠鏈€鏂扮殑鏂囨湰绱㈠紩
                        text = result.response
                        if text is None:
                            text = result.result
                        if text is not None:
                            speak_txt(conn, text)

            # 灏嗗嚱鏁版墽琛屾斁鍦ㄧ嚎绋嬫睜涓?
            conn.executor.submit(process_function_call)
            return True
        return False
    except json.JSONDecodeError as e:
        conn.logger.bind(tag=TAG).error(f"澶勭悊鎰忓浘缁撴灉鏃跺嚭閿? {e}")
        return False


def speak_txt(conn: "ConnectionHandler", text):
    # 璁板綍鏂囨湰鍒?sentence_id 鏄犲皠
    conn.tts.store_tts_text(conn.sentence_id, text)

    conn.tts.tts_text_queue.put(
        TTSMessageDTO(
            sentence_id=conn.sentence_id,
            sentence_type=SentenceType.FIRST,
            content_type=ContentType.ACTION,
        )
    )
    conn.tts.tts_one_sentence(conn, ContentType.TEXT, content_detail=text)
    conn.tts.tts_text_queue.put(
        TTSMessageDTO(
            sentence_id=conn.sentence_id,
            sentence_type=SentenceType.LAST,
            content_type=ContentType.ACTION,
        )
    )
    conn.dialogue.put(Message(role="assistant", content=text))
