"""Score the saved EfficientNetV2 checkpoint by dataset.

PlantVillage is a clean lab set and usually scores very high. PlantDoc's
official test split is the field-photo number.
"""

from __future__ import annotations

import csv
import json
from collections import defaultdict
from pathlib import Path

import torch
from PIL import Image
from torch.utils.data import DataLoader, Dataset
from torchvision import models, transforms

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "data"
MEAN = (0.485, 0.456, 0.406)
STD = (0.229, 0.224, 0.225)


class Rows(Dataset):
    def __init__(self, rows, indices):
        self.rows = rows
        self.indices = indices
        self.transform = transforms.Compose(
            [
                transforms.Resize(416),
                transforms.CenterCrop(384),
                transforms.ToTensor(),
                transforms.Normalize(MEAN, STD),
            ]
        )

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, index):
        row = self.rows[index]
        image = Image.open(row["path"]).convert("RGB")
        return self.transform(image), self.indices[row["label"]], row["source"]


def main() -> None:
    checkpoint = torch.load(ROOT / "runs" / "efficientnetv2_s_plant.pt", map_location="cpu", weights_only=False)
    labels = checkpoint["classes"]
    indices = {label: index for index, label in enumerate(labels)}
    with (DATA / "manifest.csv").open() as handle:
        rows = list(csv.DictReader(handle))
    plantdoc = [row for row in rows if row["source"] == "plantdoc" and row["split"] == "test"]
    model = models.efficientnet_v2_s(weights=None)
    model.classifier[1] = torch.nn.Linear(model.classifier[1].in_features, len(labels))
    model.load_state_dict(checkpoint["model"])
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model.to(device).eval()
    loader = DataLoader(Rows(plantdoc, indices), batch_size=32, num_workers=2)
    correct = defaultdict(int)
    total = defaultdict(int)
    with torch.no_grad():
        for images, targets, sources in loader:
            images = images.to(device)
            pred = model(images).argmax(1).cpu()
            for guess, truth, source in zip(pred.tolist(), targets.tolist(), sources):
                total[source] += 1
                correct[source] += int(guess == truth)
    report = {
        "checkpoint_val_acc": checkpoint.get("val_acc"),
        "plantdoc_test": {
            "correct": correct["plantdoc"],
            "total": total["plantdoc"],
            "accuracy": correct["plantdoc"] / max(total["plantdoc"], 1),
        },
        "classes": len(labels),
    }
    print(json.dumps(report, indent=2))
    (ROOT / "runs" / "plantdoc_test.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
