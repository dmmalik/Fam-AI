"""Build the Llama prompt. Diagnosis JSON is framed as data."""

from __future__ import annotations

import json
from typing import Any


def _region_block(region: dict[str, Any], language: str) -> str:
    return "\n".join(
        [
            region["names"].get(language, region["names"]["en"]),
            region["season"].get(language, region["season"]["en"]),
            region["irrigation"].get(language, region["irrigation"]["en"]),
            region["input_access"].get(language, region["input_access"]["en"]),
        ]
    )


def _knowledge_block(treatment: dict[str, Any], language: str) -> str:
    def lines(key: str) -> str:
        values = treatment[key].get(language) or treatment[key]["en"]
        if isinstance(values, str):
            return values
        return " ".join(values)

    return "\n".join(
        [
            lines("summary"),
            lines("immediate_actions"),
            lines("treatment"),
            lines("prevention"),
            lines("when_to_seek_help"),
            lines("caution"),
        ]
    )


def build_prompt(
    template: str,
    *,
    language: str,
    language_name: str,
    region: dict[str, Any],
    treatment: dict[str, Any],
    diagnosis: dict[str, Any],
) -> str:
    return template.format(
        language=language,
        language_name=language_name,
        region=_region_block(region, language),
        knowledge=_knowledge_block(treatment, language),
        diagnosis=json.dumps(diagnosis, ensure_ascii=False),
    )
