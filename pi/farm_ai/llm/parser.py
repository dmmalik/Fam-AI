"""Pull a single advice object out of model text."""

from __future__ import annotations

import json
from typing import Any


_KEYS = ("summary", "immediate_actions", "treatment", "prevention", "when_to_seek_help")


def parse_advice(text: str | None) -> dict[str, Any] | None:
    if not text:
        return None
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        payload = json.loads(text[start : end + 1])
    except json.JSONDecodeError:
        return None
    if not isinstance(payload, dict):
        return None
    if any(key not in payload for key in _KEYS):
        return None
    summary = payload["summary"]
    help_text = payload["when_to_seek_help"]
    if not isinstance(summary, str) or not summary.strip():
        return None
    if not isinstance(help_text, str) or not help_text.strip():
        return None
    lists: dict[str, list[str]] = {}
    for key in ("immediate_actions", "treatment", "prevention"):
        value = payload[key]
        if not isinstance(value, list) or not value:
            return None
        cleaned = [str(item).strip() for item in value if str(item).strip()]
        if not cleaned:
            return None
        lists[key] = cleaned[:6]
    return {
        "summary": summary.strip(),
        "immediate_actions": lists["immediate_actions"],
        "treatment": lists["treatment"],
        "prevention": lists["prevention"],
        "when_to_seek_help": help_text.strip(),
    }
