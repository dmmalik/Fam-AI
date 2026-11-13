"""Load labels, treatments, regions, and the advisory prompt."""

from __future__ import annotations

import json
import string
from pathlib import Path
from typing import Any

from farm_ai.config import BUILTIN_LANGUAGES, LANGUAGE_NAMES, LANGUAGES


_LANGS = BUILTIN_LANGUAGES
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
        self._packs, self._pack_paths = _load_packs(
            self.root / "packs",
            self.labels,
            self._treatments,
        )
        self.prompt = (self.root / "prompts" / "advisory.txt").read_text(encoding="utf-8")
        try:
            fields = {
                name
                for _, name, _, _ in string.Formatter().parse(self.prompt)
                if name
            }
        except ValueError as exc:
            raise ValueError("prompt has a broken placeholder") from exc
        expected = {"language", "language_name", "region", "knowledge", "diagnosis"}
        if fields != expected:
            raise ValueError("prompt placeholders must be " + ", ".join(sorted(expected)))
        self._by_id = {item["id"]: item for item in self.labels}

    def label(self, label_id: str) -> dict[str, Any]:
        return self._by_id[label_id]

    def treatment(self, label_id: str) -> dict[str, Any]:
        return self._treatments[label_id]

    def region(self, region_id: str) -> dict[str, Any] | None:
        return self._regions.get(region_id)

    def language_name(self, code: str) -> str:
        return LANGUAGE_NAMES.get(code, code)

    def pack_path(self, code: str) -> Path | None:
        return self._pack_paths.get(code)

    def localized(
        self,
        label_id: str,
        region: dict[str, Any],
        language: str,
    ) -> tuple[dict[str, Any], dict[str, Any], str]:
        """Return notes in `language` when a pack or a built-in text exists.

        The third value is the language those stored notes are actually in.
        """

        treatment = self.treatment(label_id)
        if language in _LANGS:
            return treatment, region, language
        pack = self._packs.get(language)
        if pack is None:
            return treatment, region, "en"
        region_id = str(region.get("id", ""))
        return (
            _overlay(treatment, pack["treatments"][label_id], language),
            _overlay(region, pack["regions"][region_id], language),
            language,
        )

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


def _overlay(block: dict[str, Any], translated: dict[str, Any], language: str) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for key, value in block.items():
        if key in translated and isinstance(value, dict):
            copied = dict(value)
            copied[language] = translated[key]
            out[key] = copied
        else:
            out[key] = value
    return out


def _load_packs(
    folder: Path,
    labels: list[dict[str, Any]],
    treatments: dict[str, Any],
) -> tuple[dict[str, dict[str, Any]], dict[str, Path]]:
    packs: dict[str, dict[str, Any]] = {}
    paths: dict[str, Path] = {}
    if not folder.is_dir():
        return packs, paths
    label_ids = [item["id"] for item in labels]
    allowed = set(LANGUAGES) - set(_LANGS)
    for path in sorted(folder.glob("*.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        code = _clean_pack_code(payload, path)
        if code not in allowed:
            raise ValueError(f"pack {path.name} is not a downloadable language")
        packs[code] = _clean_pack(payload, code, label_ids, treatments)
        paths[code] = path
    return packs, paths


def _clean_pack_code(payload: Any, path: Path) -> str:
    if not isinstance(payload, dict):
        raise ValueError(f"{path.name} must be an object")
    code = payload.get("code")
    if not isinstance(code, str) or path.stem != code:
        raise ValueError(f"{path.name} code does not match the file name")
    version = payload.get("version")
    if version != 1:
        raise ValueError(f"{code} pack version must be 1")
    return code


def _clean_pack(
    payload: dict[str, Any],
    code: str,
    label_ids: list[str],
    treatments: dict[str, Any],
) -> dict[str, Any]:
    raw_treatments = payload.get("treatments")
    raw_regions = payload.get("regions")
    if not isinstance(raw_treatments, dict) or not isinstance(raw_regions, dict):
        raise ValueError(f"{code} pack is missing treatments or regions")
    cleaned_treatments: dict[str, Any] = {}
    for label_id in label_ids:
        block = raw_treatments.get(label_id)
        if not isinstance(block, dict):
            raise ValueError(f"{code} pack missing {label_id}")
        entry: dict[str, Any] = {}
        for key in _TEXT_KEYS:
            value = block.get(key)
            if not isinstance(value, str) or not value.strip():
                raise ValueError(f"{code} {label_id} {key} is empty")
            entry[key] = value.strip()
        for key in _LIST_KEYS:
            value = block.get(key)
            english = treatments[label_id][key]["en"]
            if not isinstance(value, list) or len(value) != len(english):
                raise ValueError(f"{code} {label_id} {key} does not match English")
            items = [str(item).strip() for item in value]
            if any(not item for item in items):
                raise ValueError(f"{code} {label_id} {key} has an empty item")
            entry[key] = items
        cleaned_treatments[label_id] = entry
    extra = [key for key in raw_treatments if key not in set(label_ids)]
    if extra:
        raise ValueError(f"{code} pack has unknown labels")
    cleaned_regions: dict[str, Any] = {}
    for region_id in _REGION_IDS:
        block = raw_regions.get(region_id)
        if not isinstance(block, dict):
            raise ValueError(f"{code} pack missing {region_id}")
        entry = {}
        for key in _REGION_KEYS:
            value = block.get(key)
            if not isinstance(value, str) or not value.strip():
                raise ValueError(f"{code} {region_id} {key} is empty")
            entry[key] = value.strip()
        cleaned_regions[region_id] = entry
    summaries = [cleaned_treatments[label_id]["summary"] for label_id in label_ids]
    english = [treatments[label_id]["summary"]["en"] for label_id in label_ids]
    if summaries == english:
        raise ValueError(f"{code} pack still matches the English notes")
    return {"treatments": cleaned_treatments, "regions": cleaned_regions}


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
