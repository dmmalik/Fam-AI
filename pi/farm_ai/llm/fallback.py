"""Translated template advice used when Llama output is missing or invalid."""

from __future__ import annotations

from typing import Any


def _pick(block: dict[str, Any], language: str) -> Any:
    if language in block and block[language]:
        return block[language]
    return block["en"]


def fallback_advice(
    treatment: dict[str, Any],
    region: dict[str, Any],
    language: str,
) -> dict[str, Any]:
    prevention = list(_pick(treatment["prevention"], language))
    irrigation = _pick(region["irrigation"], language)
    if irrigation not in prevention:
        prevention.append(irrigation)
    return {
        "summary": _pick(treatment["summary"], language),
        "immediate_actions": list(_pick(treatment["immediate_actions"], language)),
        "treatment": list(_pick(treatment["treatment"], language)),
        "prevention": prevention,
        "when_to_seek_help": _pick(treatment["when_to_seek_help"], language),
    }
