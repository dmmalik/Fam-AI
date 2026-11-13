"""Load labels, treatments, regions, and the advisory prompt."""

from __future__ import annotations

import json
import string
from pathlib import Path
from typing import Any


_LANGS = ("en", "hi", "sw")
_LIST_KEYS = ("immediate_actions", "treatment", "prevention")
_TEXT_KEYS = ("summary", "when_to_seek_help", "caution")
_REGION_IDS = ("indo_gangetic", "peninsular_india", "east_african_highlands")
_REGION_KEYS = ("names", "season", "irrigation", "input_access")
_REQUIRED_PESTS = ("pest_aphids", "pest_fall_armyworm", "pest_whitefly", "pest_stem_borer")
_REQUIRED_CROPS = ("tomato", "potato", "cassava", "maize", "apple")


class KnowledgeBase:
    def __init__(self, root: str | Path) -> None:
        self.root = Path(root)
        self.labels = _load_labels(self.root / "labels.json")
        self._treatments = _load_treatments(self.root / "treatments.json", self.labels)
        self._regions = _load_regions(self.root / "regions.json")
        self.prompt = (self.root / "prompts" / "advisory.txt").read_text(encoding="utf-8")
        try:
            fields = {
                name
                for _, name, _, _ in string.Formatter().parse(self.prompt)
                if name
            }
        except ValueError as exc:
            raise ValueError("prompt has a broken placeholder") from exc
        expected = {"language", "region", "knowledge", "diagnosis"}
        if fields != expected:
            raise ValueError("prompt placeholders must be " + ", ".join(sorted(expected)))
        self._by_id = {item["id"]: item for item in self.labels}

    def label(self, label_id: str) -> dict[str, Any]:
        return self._by_id[label_id]

    def treatment(self, label_id: str) -> dict[str, Any]:
        return self._treatments[label_id]

    def region(self, region_id: str) -> dict[str, Any] | None:
        return self._regions.get(region_id)

    @property
    def region_ids(self) -> frozenset[str]:
        return frozenset(self._regions)


def _read_json(path: Path) -> Any:
    if not path.is_file():
        raise ValueError(f"missing {path.name}")
    return json.loads(path.read_text(encoding="utf-8"))


def _load_labels(path: Path) -> list[dict[str, Any]]:
    payload = _read_json(path)
    if not isinstance(payload, list) or not payload:
        raise ValueError("labels.json must be a non-empty list")
    seen: set[str] = set()
    labels: list[dict[str, Any]] = []
    for item in payload:
        if not isinstance(item, dict):
            raise ValueError("each label must be an object")
        for key in ("id", "crop", "kind", "display_name"):
            if not isinstance(item.get(key), str) or not item[key].strip():
                raise ValueError(f"label missing {key}")
        label_id = item["id"].strip()
        if label_id in seen:
            raise ValueError(f"duplicate label {label_id}")
        seen.add(label_id)
        kind = item["kind"].strip()
        if kind not in {"disease", "pest"}:
            raise ValueError(f"{label_id} kind must be disease or pest")
        labels.append(
            {
                "id": label_id,
                "crop": item["crop"].strip().lower(),
                "kind": kind,
                "display_name": item["display_name"].strip(),
            }
        )
    crops = {item["crop"] for item in labels}
    missing_crops = [crop for crop in _REQUIRED_CROPS if crop not in crops]
    if missing_crops:
        raise ValueError("labels missing crops: " + ", ".join(missing_crops))
    missing_pests = [pest for pest in _REQUIRED_PESTS if pest not in seen]
    if missing_pests:
        raise ValueError("labels missing pests: " + ", ".join(missing_pests))
    disease_crops = {item["crop"] for item in labels if item["kind"] == "disease"}
    for crop in sorted(disease_crops):
        if not any(item["crop"] == crop and item["id"].endswith("_healthy") for item in labels):
            raise ValueError(f"{crop} is missing a healthy class")
    return labels


def _load_treatments(path: Path, labels: list[dict[str, Any]]) -> dict[str, Any]:
    payload = _read_json(path)
    if not isinstance(payload, dict):
        raise ValueError("treatments.json must be an object keyed by label id")
    label_ids = [item["id"] for item in labels]
    missing = [label_id for label_id in label_ids if label_id not in payload]
    if missing:
        raise ValueError("treatments missing: " + ", ".join(missing))
    extra = [key for key in payload if key not in set(label_ids)]
    if extra:
        raise ValueError("treatments have unknown labels: " + ", ".join(sorted(extra)))
    cleaned: dict[str, Any] = {}
    for label_id in label_ids:
        block = payload[label_id]
        if not isinstance(block, dict):
            raise ValueError(f"{label_id} treatment must be an object")
        entry: dict[str, Any] = {}
        for key in _TEXT_KEYS:
            entry[key] = _language_text(block.get(key), f"{label_id} {key}")
        for key in _LIST_KEYS:
            entry[key] = _language_list(block.get(key), f"{label_id} {key}")
        cleaned[label_id] = entry
    return cleaned


def _load_regions(path: Path) -> dict[str, Any]:
    payload = _read_json(path)
    if not isinstance(payload, dict):
        raise ValueError("regions.json must be an object")
    missing = [region_id for region_id in _REGION_IDS if region_id not in payload]
    if missing:
        raise ValueError("regions missing: " + ", ".join(missing))
    cleaned: dict[str, Any] = {}
    for region_id in _REGION_IDS:
        block = payload[region_id]
        if not isinstance(block, dict):
            raise ValueError(f"{region_id} must be an object")
        entry: dict[str, Any] = {"id": region_id}
        for key in _REGION_KEYS:
            entry[key] = _language_text(block.get(key), f"{region_id} {key}")
        cleaned[region_id] = entry
    return cleaned


def _language_text(block: Any, name: str) -> dict[str, str]:
    if not isinstance(block, dict):
        raise ValueError(f"{name} must map language codes to text")
    cleaned: dict[str, str] = {}
    for language in _LANGS:
        value = block.get(language)
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"{name} missing {language}")
        cleaned[language] = value.strip()
    return cleaned


def _language_list(block: Any, name: str) -> dict[str, list[str]]:
    if not isinstance(block, dict):
        raise ValueError(f"{name} must map language codes to lists")
    cleaned: dict[str, list[str]] = {}
    for language in _LANGS:
        value = block.get(language)
        if not isinstance(value, list) or not value:
            raise ValueError(f"{name} missing {language}")
        items = [str(item).strip() for item in value]
        if any(not item for item in items):
            raise ValueError(f"{name} {language} has an empty item")
        cleaned[language] = items
    expected = len(cleaned["en"])
    for language in _LANGS:
        if len(cleaned[language]) != expected:
            raise ValueError(f"{name} {language} does not match English")
    return cleaned
