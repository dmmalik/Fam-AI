"""Advisory engine. A parsed Llama object wins; otherwise the translated template."""

from __future__ import annotations

from farm_ai.config import Settings
from farm_ai.llm.executorch_llama import ExecuTorchLlama
from farm_ai.llm.fallback import fallback_advice
from farm_ai.llm.parser import parse_advice
from farm_ai.llm.prompt import build_prompt
from farm_ai.vision.factory import BackendError


class MockLlama:
    name = "mock"
    loaded = False

    def warm(self) -> None:
        return None

    def generate(self, prompt: str) -> str | None:
        del prompt
        return None


def create_llm(settings: Settings):
    forced = settings.llm_backend
    if forced not in {None, "mock", "executorch"}:
        raise BackendError(f"unknown llm backend: {forced}")
    if forced == "executorch" or (forced is None and settings.llama_pte.is_file()):
        try:
            backend = ExecuTorchLlama(settings.llama_pte)
        except (FileNotFoundError, RuntimeError) as exc:
            raise BackendError(str(exc)) from exc
    else:
        backend = MockLlama()
    backend.warm()
    return backend


class TemplateAdvisor:
    """Advice notes only. The Hailo vision path does not load Llama."""

    name = "template"
    loaded = False

    def warm(self) -> None:
        return None

    def generate(self, prompt: str) -> str | None:
        del prompt
        return None


def llm_for_vision(settings: Settings, vision_name: str):
    if vision_name == "hailo":
        advisor = TemplateAdvisor()
        advisor.warm()
        return advisor
    return create_llm(settings)


def advise(llm, kb, *, language: str, region: dict, treatment: dict, diagnosis: dict) -> tuple[dict, str]:
    prompt = build_prompt(
        kb.prompt,
        language=language,
        region=region,
        treatment=treatment,
        diagnosis=diagnosis,
    )
    raw = None
    try:
        raw = llm.generate(prompt)
    except Exception:
        raw = None
    parsed = parse_advice(raw)
    if parsed:
        return parsed, "llama"
    return fallback_advice(treatment, region, language), "template"
