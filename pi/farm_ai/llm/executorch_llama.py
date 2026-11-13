"""Llama 3.2 Instruct through ExecuTorch on the Arm CPU.

Generation is capped at 256 new tokens. This runner never touches HailoRT.
The exported program is expected to expose a `generate` or `forward` method
that accepts the prompt string, matching ExecuTorch's Llama runner. If the
installed runtime only offers the lower-level method API, the call is isolated
in `_generate`.
"""

from __future__ import annotations

from pathlib import Path

from farm_ai.config import LLAMA_MAX_NEW_TOKENS


class ExecuTorchLlama:
    name = "executorch"
    loaded = True

    def __init__(self, model_path: Path, max_new_tokens: int = LLAMA_MAX_NEW_TOKENS) -> None:
        if not model_path.is_file():
            raise FileNotFoundError(f"Llama .pte not found: {model_path}")
        self.max_new_tokens = max_new_tokens
        self._method = _load(model_path)

    def warm(self) -> None:
        self.generate('{"summary":"ok","immediate_actions":["wait"],"treatment":["none"],"prevention":["none"],"when_to_seek_help":"none"}')

    def generate(self, prompt: str) -> str | None:
        return _generate(self._method, prompt, self.max_new_tokens)


def _load(path: Path):
    try:
        from executorch.runtime import Runtime
    except ImportError as exc:
        raise RuntimeError("ExecuTorch runtime is not installed on this device") from exc
    program = Runtime.get().load_program(str(path))
    for method_name in ("generate", "forward"):
        try:
            return program.load_method(method_name)
        except Exception:
            continue
    raise RuntimeError("Llama .pte has neither generate nor forward")


def _generate(method, prompt: str, max_new_tokens: int) -> str:
    try:
        outputs = method.execute((prompt, max_new_tokens))
    except TypeError:
        outputs = method.execute([prompt])
    value = outputs[0]
    if isinstance(value, bytes):
        return value.decode("utf-8", errors="replace")
    return str(value)
