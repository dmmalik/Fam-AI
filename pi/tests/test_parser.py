import json

from farm_ai.kb.retrieve import KnowledgeBase
from farm_ai.llm.factory import advise
from farm_ai.llm.parser import parse_advice

from conftest import KB


def _valid(**overrides):
    payload = {
        "summary": "Keep watching the lower leaves.",
        "immediate_actions": ["Remove the worst leaf"],
        "treatment": ["Water at the base"],
        "prevention": ["Rotate next season"],
        "when_to_seek_help": "If the spots reach the top",
    }
    payload.update(overrides)
    return payload


def test_parse_advice_accepts_json_with_extra_text():
    text = "Advice follows.\n" + json.dumps(_valid()) + "\nDone."
    parsed = parse_advice(text)
    assert parsed["summary"] == "Keep watching the lower leaves."
    assert parsed["immediate_actions"] == ["Remove the worst leaf"]


def test_parse_advice_rejects_bad_shapes():
    assert parse_advice(None) is None
    assert parse_advice("") is None
    assert parse_advice("not json") is None
    assert parse_advice("{not json}") is None
    assert parse_advice(json.dumps([1, 2])) is None
    missing = _valid()
    del missing["prevention"]
    assert parse_advice(json.dumps(missing)) is None
    assert parse_advice(json.dumps(_valid(immediate_actions=[]))) is None
    assert parse_advice(json.dumps(_valid(summary="  "))) is None
    assert parse_advice(json.dumps(_valid(when_to_seek_help=""))) is None


def test_parse_advice_cleans_lists():
    parsed = parse_advice(
        json.dumps(
            _valid(
                immediate_actions=["  one  ", 2, " ", "three", "four", "five", "six", "seven"]
            )
        )
    )
    assert parsed["immediate_actions"] == ["one", "2", "three", "four", "five", "six"]


def test_bad_llama_json_uses_template():
    kb = KnowledgeBase(KB)

    class Bad:
        def __init__(self) -> None:
            self.prompt = ""

        def generate(self, prompt: str) -> str:
            self.prompt = prompt
            return "I cannot answer as JSON"

    bad = Bad()
    advice, source, stored = advise(
        bad,
        kb,
        language="sw",
        region=kb.region("east_african_highlands"),
        treatment=kb.treatment("tomato_late_blight"),
        diagnosis={"label_id": "tomato_late_blight", "severity": "high"},
    )
    assert "tomato_late_blight" in bad.prompt
    assert "Swahili" in bad.prompt
    assert source == "template"
    assert stored == "sw"
    assert advice["summary"] == kb.treatment("tomato_late_blight")["summary"]["sw"]
    assert advice["summary"] != kb.treatment("tomato_late_blight")["summary"]["en"]


def test_valid_llama_json_wins():
    kb = KnowledgeBase(KB)
    payload = _valid()

    class Good:
        def generate(self, prompt: str) -> str:
            return json.dumps(payload)

    advice, source, stored = advise(
        Good(),
        kb,
        language="hi",
        region=kb.region("indo_gangetic"),
        treatment=kb.treatment("maize_healthy"),
        diagnosis={"label_id": "maize_healthy"},
    )
    assert source == "llama"
    assert stored == "hi"
    assert advice == {
        "summary": payload["summary"],
        "immediate_actions": payload["immediate_actions"],
        "treatment": payload["treatment"],
        "prevention": payload["prevention"],
        "when_to_seek_help": payload["when_to_seek_help"],
    }


def test_tamil_template_uses_the_pack_not_english():
    kb = KnowledgeBase(KB)

    class Bad:
        def __init__(self) -> None:
            self.prompt = ""

        def generate(self, prompt: str) -> str:
            self.prompt = prompt
            return "not json"

    bad = Bad()
    advice, source, stored = advise(
        bad,
        kb,
        language="ta",
        region=kb.region("indo_gangetic"),
        treatment=kb.treatment("tomato_late_blight"),
        diagnosis={"label_id": "tomato_late_blight", "severity": "high"},
    )
    assert source == "template"
    assert "Tamil" in bad.prompt
    if kb.pack_path("ta") is None:
        assert stored == "en"
        assert advice["summary"] == kb.treatment("tomato_late_blight")["summary"]["en"]
    else:
        assert stored == "ta"
        assert advice["summary"] != kb.treatment("tomato_late_blight")["summary"]["en"]


def test_notes_cover_every_label_in_three_languages():
    kb = KnowledgeBase(KB)
    assert [item["crop"] for item in kb.labels if item["id"] == "pest_fall_armyworm"] == ["maize"]
    assert kb.region("indo_gangetic")["irrigation"]["hi"]
    block = kb.treatment("potato_late_blight")
    assert block["summary"]["hi"] != block["summary"]["en"]
    assert block["summary"]["sw"] != block["summary"]["en"]
    assert block["caution"]["sw"]
