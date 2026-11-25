"""Export the trained EfficientNetV2-S checkpoint to float32 TFLite.

Float16 weights made the phone return one class for every photo. The phone
feeds float32 pixels. Input layout is recorded in leaf_meta.json.
"""

from __future__ import annotations

import json
from pathlib import Path

import torch
from torchvision import models

ROOT = Path(__file__).resolve().parents[1]
CHECKPOINT = ROOT / "train" / "runs" / "efficientnetv2_s_plant.pt"
OUT = ROOT / "android" / "app" / "src" / "main" / "assets" / "leaf"


def load_model():
    checkpoint = torch.load(CHECKPOINT, map_location="cpu", weights_only=False)
    classes = list(checkpoint["classes"])
    model = models.efficientnet_v2_s(weights=None)
    model.classifier[1] = torch.nn.Linear(model.classifier[1].in_features, len(classes))
    model.load_state_dict(checkpoint["model"])
    model.eval()
    return model, classes


def main() -> None:
    import litert_torch
    from ai_edge_litert.interpreter import Interpreter

    model, classes = load_model()
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "classes.txt").write_text("\n".join(classes) + "\n")
    sample = torch.randn(1, 3, 384, 384)
    path = OUT / "efficientnetv2_s_plant.tflite"
    edge = litert_torch.convert(
        model,
        (sample,),
        fold_fp16_resource_casts=False,
    )
    tmp = Path("/tmp/efficientnetv2_s_plant.tflite")
    edge.export(str(tmp))
    path.write_bytes(tmp.read_bytes())
    engine = Interpreter(model_path=str(path))
    shape = engine.get_input_details()[0]["shape"].tolist()
    with torch.no_grad():
        expected = int(model(sample).argmax(dim=1))
    engine.allocate_tensors()
    engine.set_tensor(engine.get_input_details()[0]["index"], sample.numpy())
    engine.invoke()
    output = engine.get_tensor(engine.get_output_details()[0]["index"])
    got = int(output.argmax())
    meta = {
        "arch": "efficientnet_v2_s",
        "image_size": 384,
        "resize": 416,
        "mean": [0.485, 0.456, 0.406],
        "std": [0.229, 0.224, 0.225],
        "classes": len(classes),
        "input_shape": shape,
        "bytes": path.stat().st_size,
        "check_class": classes[got],
    }
    (OUT / "leaf_meta.json").write_text(json.dumps(meta, indent=2) + "\n")
    print(
        f"saved {path} {path.stat().st_size} classes {len(classes)} shape {shape} match {got == expected}",
        flush=True,
    )
    if got != expected:
        raise SystemExit(f"top class drifted: tflite {got} pytorch {expected}")


if __name__ == "__main__":
    main()
