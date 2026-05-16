import sys
import unittest
from datetime import datetime, timedelta, timezone
from types import ModuleType
from types import SimpleNamespace
from pathlib import Path
from unittest.mock import AsyncMock, Mock, patch


ROOT = Path(__file__).resolve().parents[2]
SERVER_ROOT = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "xiaozhi-server"
M4_VOICEPRINT_STATUS = ROOT / "docs" / "M4.10-voiceprint-demo-status.md"
sys.path.insert(0, str(SERVER_ROOT))


class _BoundLogger:
    def warning(self, *args, **kwargs):
        pass

    def info(self, *args, **kwargs):
        pass

    def debug(self, *args, **kwargs):
        pass

    def error(self, *args, **kwargs):
        pass


class _Logger:
    def bind(self, **kwargs):
        return _BoundLogger()


logger_module = ModuleType("config.logger")
logger_module.setup_logging = lambda: _Logger()
sys.modules.setdefault("config.logger", logger_module)


async def _fake_audio_to_data(*args, **kwargs):
    return []


util_module = sys.modules.get("core.utils.util", ModuleType("core.utils.util"))
util_module.remove_punctuation_and_length = lambda text: (0, str(text or "").replace("，", "").replace(",", ""))
util_module.audio_to_data = _fake_audio_to_data
util_module.opus_datas_to_wav_bytes = lambda *args, **kwargs: b""
sys.modules["core.utils.util"] = util_module

wakeup_word_module = ModuleType("core.utils.wakeup_word")
wakeup_word_module.WakeupWordsConfig = lambda: SimpleNamespace(
    get_wakeup_response=lambda *args, **kwargs: None,
    generate_file_path=lambda *args, **kwargs: "",
    update_wakeup_response=lambda *args, **kwargs: None,
)
sys.modules.setdefault("core.utils.wakeup_word", wakeup_word_module)

device_mcp_module = ModuleType("core.providers.tools.device_mcp")
device_mcp_module.MCPClient = lambda *args, **kwargs: SimpleNamespace()
device_mcp_module.send_mcp_initialize_message = AsyncMock()
sys.modules.setdefault("core.providers.tools.device_mcp", device_mcp_module)

report_handle_module = ModuleType("core.handle.reportHandle")
report_handle_module.enqueue_tool_report = lambda *args, **kwargs: None
sys.modules.setdefault("core.handle.reportHandle", report_handle_module)

from core.utils.voiceprint_cache import (  # noqa: E402
    ActiveVoiceprintCache,
    VoiceprintEntry,
    decide_voiceprint_policy,
    refresh_voiceprint_cache_from_business,
)
from core.handle.intentHandler import handle_user_intent, submit_voice_task, voice_task_confirmation_text  # noqa: E402


def _conn_with_cache(mode="strict"):
    cache = ActiveVoiceprintCache()
    cache.update_device(
        "dev-1",
        [
            {
                "memberId": 1,
                "displayName": "Parent",
                "memberType": "owner",
                "speakerRef": "local:parent",
                "embeddingHash": "a" * 64,
                "status": "active",
            }
        ],
    )
    return SimpleNamespace(
        config={"voiceprint": {"mode": mode, "cache_ttl_seconds": 300}},
        device_id="dev-1",
        voiceprint_cache=cache,
        logger=_Logger(),
        cmd_exit=[],
        intent_type="function_call",
        current_speaker=None,
        current_speaker_ref=None,
        func_handler=None,
        tts=None,
        sentence_id=None,
        dialogue=SimpleNamespace(put=Mock()),
    )


def _conn_with_child_cache(mode="child", expires_at=None):
    cache = ActiveVoiceprintCache()
    payload = {
        "memberId": 2,
        "displayName": "Child",
        "memberType": "child",
        "speakerRef": "local:child",
        "embeddingHash": "b" * 64,
        "status": "active",
    }
    if expires_at is not None:
        payload["expiresAt"] = expires_at
    cache.update_device("dev-1", [payload])
    conn = _conn_with_cache(mode)
    conn.voiceprint_cache = cache
    return conn


class VoiceprintCacheTests(unittest.TestCase):
    def test_voiceprint_demo_status_keeps_real_world_gates_open(self):
        text = M4_VOICEPRINT_STATUS.read_text(encoding="utf-8", errors="replace")

        for token in (
            "Evidence Boundary",
            "local/design-time evidence only",
            "not a completion declaration",
            "does not replace real audio voiceprint comparison",
            "Voiceprint Demo Evidence Gap Record",
            "missing evidence scope: real audio voiceprint comparison, production ASR `speaker_ref`, child voiceprint re-enrollment capture, production push notification for parent confirmation, mini program primary approval on a real WeChat client, or U8/U1 real-device task execution",
            "fallback path: keep voiceprint behavior labeled demo/design-time, keep primary review available for unknown or child speakers, avoid production biometric claims, and block release if child re-enrollment or parent confirmation evidence is missing",
            "rollback trigger: real audio matching fails, ASR `speaker_ref` is missing or unstable, child re-enrollment cannot be captured, primary approval card omits speaker context, production push is required but unavailable, or U8/U1 execution cannot be verified",
            "follow-up evidence: redacted audio-match log, ASR `speaker_ref` trace, voiceprint cache payload, child re-enrollment capture screenshot, parent approval screenshot, push delivery evidence or deferral record, BusinessServer task constraints row, and U8/U1 execution log",
        ):
            self.assertIn(token, text)

    def test_cache_keeps_only_active_voiceprints_for_device(self):
        cache = ActiveVoiceprintCache()

        entries = cache.update_device(
            "dev-1",
            [
                {
                    "memberId": 1,
                    "displayName": "Parent",
                    "memberType": "owner",
                    "speakerRef": "local:parent",
                    "embeddingHash": "a" * 64,
                    "status": "active",
                },
                {
                    "memberId": 2,
                    "displayName": "Disabled",
                    "memberType": "member",
                    "speakerRef": "local:disabled",
                    "embeddingHash": "b" * 64,
                    "status": "disabled",
                },
            ],
        )

        self.assertEqual(len(entries), 1)
        self.assertEqual(cache.entries_for_device("dev-1")[0].speaker_ref, "local:parent")

    def test_refresh_clears_stale_cache_when_business_reports_disposed_device(self):
        class _Response:
            status_code = 200

            def json(self):
                return {"code": 403, "msg": "E_DEVICE_DISPOSED: device is disposed"}

        class _Client:
            def __init__(self, *args, **kwargs):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, *args):
                return False

            async def post(self, *args, **kwargs):
                return _Response()

        cache = ActiveVoiceprintCache()
        cache.update_device(
            "dev-1",
            [
                {
                    "memberId": 1,
                    "displayName": "Parent",
                    "memberType": "owner",
                    "speakerRef": "local:parent",
                    "embeddingHash": "a" * 64,
                    "status": "active",
                }
            ],
        )

        config = {
            "server": {
                "voiceprint_cache_business_base_url": "http://business.local",
                "internal_motion_task_token": "secret-token",
            }
        }
        with patch("core.utils.voiceprint_cache.httpx.AsyncClient", new=_Client):
            entries = asyncio_run(refresh_voiceprint_cache_from_business(config, "dev-1", cache, _Logger()))

        self.assertEqual([], entries)
        self.assertEqual([], cache.entries_for_device("dev-1"))

    def test_policy_off_allows_without_speaker(self):
        decision = decide_voiceprint_policy("voiceprint_off", [], None)

        self.assertTrue(decision["allowed"])
        self.assertEqual(decision["reason"], "voiceprint_off")

    def test_policy_strict_rejects_unknown_speaker(self):
        entries = [VoiceprintEntry(1, "Parent", "owner", "local:parent", "a" * 64)]

        decision = decide_voiceprint_policy("strict", entries, "local:unknown")

        self.assertFalse(decision["allowed"])
        self.assertEqual(decision["reason"], "unknown_rejected")

    def test_policy_loose_allows_unknown_speaker(self):
        entries = [VoiceprintEntry(1, "Parent", "owner", "local:parent", "a" * 64)]

        decision = decide_voiceprint_policy("loose", entries, "local:unknown")

        self.assertTrue(decision["allowed"])
        self.assertEqual(decision["reason"], "unknown_allowed")

    def test_policy_returns_matched_member(self):
        entry = VoiceprintEntry(1, "Parent", "owner", "local:parent", "a" * 64)

        decision = decide_voiceprint_policy("strict", [entry], "local:parent")

        self.assertTrue(decision["allowed"])
        self.assertEqual(decision["reason"], "matched")
        self.assertEqual(decision["member"], entry)

    def test_policy_child_mode_allows_unknown_with_child_fallback(self):
        entry = VoiceprintEntry(2, "Child", "child", "local:child", "b" * 64)

        decision = decide_voiceprint_policy("child", [entry], "local:unknown")

        self.assertTrue(decision["allowed"])
        self.assertEqual(decision["reason"], "child_unknown_allowed")

    def test_policy_child_expired_match_requests_reenroll(self):
        expired = datetime.now(timezone.utc) - timedelta(days=1)
        entry = VoiceprintEntry(2, "Child", "child", "local:child", "b" * 64, expires_at=expired)

        decision = decide_voiceprint_policy("child", [entry], "local:child")

        self.assertTrue(decision["allowed"])
        self.assertEqual(decision["reason"], "child_reenroll_required")
        self.assertEqual(decision["member"], entry)

    def test_handle_user_intent_strict_rejects_unmatched_asr_speaker_ref(self):
        conn = _conn_with_cache("strict")
        payload = '{"content":"\\u5c0f\\u667a\\uff0c\\u5199\\u4f60\\u597d","speaker_ref":"local:child"}'

        with patch("core.handle.intentHandler.send_stt_message", new=AsyncMock()) as send_stt, \
                patch("core.handle.intentHandler.speak_txt", new=Mock()) as speak_txt:
            handled = asyncio_run(handle_user_intent(conn, payload))

        self.assertTrue(handled)
        send_stt.assert_awaited_once_with(conn, "\u5c0f\u667a\uff0c\u5199\u4f60\u597d")
        speak_txt.assert_called_once()

    def test_handle_user_intent_strict_allows_matched_asr_speaker_ref(self):
        conn = _conn_with_cache("strict")
        payload = '{"content":"\\u666e\\u901a\\u804a\\u5929","speaker_ref":"local:parent"}'

        with patch("core.handle.intentHandler.checkWakeupWords", new=AsyncMock(return_value=False)):
            handled = asyncio_run(handle_user_intent(conn, payload))

        self.assertFalse(handled)
        self.assertEqual(conn.current_member_id, 1)
        self.assertEqual(conn.current_speaker, "Parent")

    def test_handle_user_intent_voiceprint_off_ignores_missing_speaker_ref(self):
        conn = _conn_with_cache("voiceprint_off")
        conn.config["enable_wakeup_words_response_cache"] = False

        with patch("core.handle.intentHandler.checkWakeupWords", new=AsyncMock(return_value=False)):
            handled = asyncio_run(handle_user_intent(conn, "\u666e\u901a\u804a\u5929"))

        self.assertFalse(handled)

    def test_handle_user_intent_child_mode_sets_reenroll_hint_for_expired_child(self):
        expired = (datetime.now(timezone.utc) - timedelta(days=1)).isoformat()
        conn = _conn_with_child_cache("child", expired)
        payload = '{"content":"\\u666e\\u901a\\u804a\\u5929","speaker_ref":"local:child"}'

        with patch("core.handle.intentHandler.checkWakeupWords", new=AsyncMock(return_value=False)):
            handled = asyncio_run(handle_user_intent(conn, payload))

        self.assertFalse(handled)
        self.assertEqual(conn.current_member_id, 2)
        self.assertEqual(conn.current_speaker, "Child")
        self.assertTrue(conn.voiceprint_reenroll_hint)

    def test_submit_voice_task_includes_matched_voiceprint_constraints(self):
        class _Response:
            status_code = 200
            text = "ok"

            def json(self):
                return {"data": {"approvalRequiredBy": "primary"}}

        class _Client:
            def __init__(self, *args, **kwargs):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, *args):
                return False

            async def post(self, url, json=None, headers=None):
                captured["url"] = url
                captured["json"] = json
                captured["headers"] = headers
                return _Response()

        captured = {}
        conn = _conn_with_cache("strict")
        conn.config["server"] = {
            "voice_task_business_base_url": "http://business.local",
            "internal_motion_task_token": "secret-token",
        }
        conn.current_member_id = 1
        conn.current_speaker = "Parent"
        conn.current_speaker_ref = "local:parent"
        conn.current_voiceprint_member_type = "owner"
        conn.current_voiceprint_reason = "matched"
        conn.tts = SimpleNamespace(store_tts_text=Mock())

        with patch("core.handle.intentHandler.httpx.AsyncClient", new=_Client), \
                patch("core.handle.intentHandler.speak_txt", new=Mock()) as speak_txt:
            submitted = asyncio_run(submit_voice_task(conn, {"capability": "write_text", "params": {"text": "\u4f60\u597d"}}, "\u5c0f\u667a\uff0c\u5199\u4f60\u597d"))

        self.assertTrue(submitted)
        self.assertEqual("http://business.local/internal/v1/voice_task", captured["url"])
        self.assertEqual("Bearer secret-token", captured["headers"]["Authorization"])
        payload = captured["json"]
        self.assertEqual("voice", payload["source"])
        self.assertEqual("dev-1", payload["device_id"])
        voiceprint = payload["constraints"]["voiceprint"]
        self.assertTrue(voiceprint["matched"])
        self.assertEqual(1, voiceprint["member_id"])
        self.assertEqual("Parent", voiceprint["display_name"])
        self.assertEqual("owner", voiceprint["member_type"])
        self.assertEqual("local:parent", voiceprint["speaker_ref"])
        self.assertEqual("matched", voiceprint["reason"])
        speak_txt.assert_called_once_with(conn, "\u4efb\u52a1\u5df2\u63d0\u4ea4\uff0c\u7b49\u5f85\u5bb6\u957f\u786e\u8ba4\u3002")

    def test_submit_voice_task_treats_business_result_error_as_failure(self):
        class _Response:
            status_code = 200
            text = "business rejected"

            def json(self):
                return {"code": 403, "msg": "E_DEVICE_DISPOSED: device is disposed"}

        class _Client:
            def __init__(self, *args, **kwargs):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, *args):
                return False

            async def post(self, *args, **kwargs):
                return _Response()

        conn = _conn_with_cache("strict")
        conn.config["server"] = {
            "voice_task_business_base_url": "http://business.local",
            "internal_motion_task_token": "secret-token",
        }
        conn.tts = SimpleNamespace(store_tts_text=Mock())

        with patch("core.handle.intentHandler.httpx.AsyncClient", new=_Client), \
                patch("core.handle.intentHandler.speak_txt", new=Mock()) as speak_txt:
            submitted = asyncio_run(submit_voice_task(conn, {"capability": "write_text", "params": {"text": "\u4f60\u597d"}}, "\u5c0f\u667a\uff0c\u5199\u4f60\u597d"))

        self.assertTrue(submitted)
        conn.dialogue.put.assert_not_called()
        speak_txt.assert_called_once_with(conn, "\u8bed\u97f3\u4efb\u52a1\u63d0\u4ea4\u5931\u8d25\u3002")

    def test_voice_task_confirmation_mentions_child_reenroll_and_primary_approval(self):
        conn = _conn_with_child_cache("child")
        conn.current_voiceprint_reason = "child_reenroll_required"
        conn.voiceprint_reenroll_hint = True

        text = voice_task_confirmation_text(conn, {"data": {"approvalRequiredBy": "primary"}})

        self.assertIn("\u7b49\u5f85\u5bb6\u957f\u786e\u8ba4", text)
        self.assertIn("\u58f0\u7eb9\u9700\u8981\u91cd\u65b0\u5f55\u5165", text)

    def test_voice_task_confirmation_mentions_unknown_speaker_primary_review(self):
        conn = _conn_with_child_cache("child")
        conn.current_voiceprint_reason = "child_unknown_allowed"

        text = voice_task_confirmation_text(conn, {"data": {"approvalRequiredBy": "primary"}})

        self.assertIn("\u672a\u5339\u914d\u5230\u767b\u8bb0\u58f0\u7eb9", text)
        self.assertIn("\u7b49\u5f85\u5bb6\u957f\u786e\u8ba4", text)


def asyncio_run(awaitable):
    import asyncio

    return asyncio.run(awaitable)


if __name__ == "__main__":
    unittest.main()
