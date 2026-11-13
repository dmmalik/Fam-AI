"""Runtime settings.

Weights are read from pi/models unless FAM_AI_CLASSIFIER_PTE,
FAM_AI_CLASSIFIER_HEF, or FAM_AI_LLAMA_PTE point somewhere else. The bearer
token is read from FAM_AI_TOKEN_FILE, which must sit outside the repo and be
mode 0600. FAM_AI_HOST is the hotspot address to bind. FAM_AI_DB is the audit
database. FAM_AI_KB points at the notes directory.

Language codes come from kb/languages.json. English, Hindi, and Swahili are
built in. Every other code is a downloadable crop-note pack.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from pathlib import Path


MODEL_ID = "efficientnetv2_s_int8_384"
LLAMA_MAX_NEW_TOKENS = 256
BUILTIN_LANGUAGES = ("en", "hi", "sw")

_PACKAGE_ROOT = Path(__file__).resolve().parents[1]
_REPO_ROOT = _PACKAGE_ROOT.parent


def default_kb_dir() -> Path:
    candidates = [
        _REPO_ROOT / "kb",
        _PACKAGE_ROOT / "kb",
        Path.cwd() / "kb",
        Path.cwd().parent / "kb",
    ]
    for candidate in candidates:
        if (candidate / "labels.json").is_file():
            return candidate
    return _REPO_ROOT / "kb"


def load_languages(kb_dir: Path | None = None) -> tuple[dict[str, object], ...]:
    path = (kb_dir or default_kb_dir()) / "languages.json"
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, list) or not payload:
        raise RuntimeError("languages.json must be a non-empty list")
    rows: list[dict[str, object]] = []
    seen: set[str] = set()
    for item in payload:
        if not isinstance(item, dict):
            raise RuntimeError("each language must be an object")
        code = str(item.get("code", "")).strip()
        name = str(item.get("name_en", "")).strip()
        autonym = str(item.get("autonym", "")).strip()
        tts = item.get("tts")
        builtin = item.get("builtin")
        if (
            not code.isalpha()
            or not code.islower()
            or not 2 <= len(code) <= 8
            or code in seen
            or not name
            or not autonym
            or not isinstance(tts, list)
            or not tts
            or not all(isinstance(tag, str) and tag.strip() for tag in tts)
            or not isinstance(builtin, bool)
        ):
            raise RuntimeError(f"bad language entry: {code or item}")
        seen.add(code)
        rows.append(
            {
                "code": code,
                "name_en": name,
                "autonym": autonym,
                "tts": [tag.strip() for tag in tts],
                "builtin": builtin,
            }
        )
    builtin = tuple(row["code"] for row in rows if row["builtin"])
    if builtin != BUILTIN_LANGUAGES:
        raise RuntimeError("built-in languages must be English, Hindi, and Swahili")
    return tuple(rows)


_LANGUAGES = load_languages()
LANGUAGES = tuple(str(row["code"]) for row in _LANGUAGES)
LANGUAGE_NAMES = {str(row["code"]): str(row["name_en"]) for row in _LANGUAGES}


def _env_path(name: str, default: Path) -> Path:
    value = os.environ.get(name, "").strip()
    return Path(value) if value else default


def _env_optional(name: str) -> str | None:
    value = os.environ.get(name, "").strip()
    return value or None


def _env_bool(name: str) -> bool:
    return os.environ.get(name, "").strip().lower() in {"1", "true", "yes", "on"}


@dataclass
class Settings:
    vision_backend: str | None = None
    llm_backend: str | None = None
    hailo_enabled: bool = False
    classifier_pte: Path = field(
        default_factory=lambda: _PACKAGE_ROOT / "models" / "efficientnetv2_s_int8_384.pte"
    )
    classifier_hef: Path = field(
        default_factory=lambda: _PACKAGE_ROOT / "models" / "efficientnetv2_s_int8_384.hef"
    )
    llama_pte: Path = field(
        default_factory=lambda: _PACKAGE_ROOT / "models" / "llama32_1b_instruct.pte"
    )
    kb_dir: Path = field(default_factory=default_kb_dir)
    token_path: Path = field(default_factory=lambda: Path("/var/lib/fam-ai/token"))
    db_path: Path = field(default_factory=lambda: Path("/var/lib/fam-ai/audit.db"))
    host: str = "192.168.4.1"
    port: int = 8080
    max_upload_bytes: int = 2 * 1024 * 1024
    diagnose_per_minute: int = 10

    @classmethod
    def from_env(cls) -> Settings:
        port_text = os.environ.get("FAM_AI_PORT", "8080").strip() or "8080"
        host = os.environ.get("FAM_AI_HOST", "192.168.4.1").strip() or "192.168.4.1"
        return cls(
            vision_backend=_env_optional("FAM_AI_VISION_BACKEND"),
            llm_backend=_env_optional("FAM_AI_LLM_BACKEND"),
            hailo_enabled=_env_bool("FAM_AI_HAILO"),
            classifier_pte=_env_path(
                "FAM_AI_CLASSIFIER_PTE",
                _PACKAGE_ROOT / "models" / "efficientnetv2_s_int8_384.pte",
            ),
            classifier_hef=_env_path(
                "FAM_AI_CLASSIFIER_HEF",
                _PACKAGE_ROOT / "models" / "efficientnetv2_s_int8_384.hef",
            ),
            llama_pte=_env_path(
                "FAM_AI_LLAMA_PTE",
                _PACKAGE_ROOT / "models" / "llama32_1b_instruct.pte",
            ),
            kb_dir=_env_path("FAM_AI_KB", default_kb_dir()),
            token_path=_env_path("FAM_AI_TOKEN_FILE", Path("/var/lib/fam-ai/token")),
            db_path=_env_path("FAM_AI_DB", Path("/var/lib/fam-ai/audit.db")),
            host=host,
            port=int(port_text),
        )
