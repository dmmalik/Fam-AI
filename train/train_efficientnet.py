"""Fine-tune EfficientNetV2-S at 384 on the prepared plant-disease manifest.

The 4060 Ti has 16 GB. The script starts at batch 32 with mixed precision and
drops the batch if that does not fit. ImageNet normalization matches the Pi
export notes. The checkpoint stores the class list so a later phone or Pi
file can keep the same logit order.
"""

from __future__ import annotations

import argparse
import csv
import json
import random
from collections import defaultdict
from pathlib import Path

import torch
from PIL import Image
from torch import nn
from torch.utils.data import DataLoader, Dataset, WeightedRandomSampler
from torchvision import models, transforms

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "data"
MEAN = (0.485, 0.456, 0.406)
STD = (0.229, 0.224, 0.225)


class LeafSet(Dataset):
    def __init__(self, rows: list[dict], indices: dict[str, int], train: bool):
        self.rows = rows
        self.indices = indices
        if train:
            self.transform = transforms.Compose(
                [
                    transforms.Resize(416),
                    transforms.RandomResizedCrop(384, scale=(0.7, 1.0)),
                    transforms.RandomHorizontalFlip(),
                    transforms.ColorJitter(0.2, 0.2, 0.2, 0.05),
                    transforms.ToTensor(),
                    transforms.Normalize(MEAN, STD),
                ]
            )
        else:
            self.transform = transforms.Compose(
                [
                    transforms.Resize(416),
                    transforms.CenterCrop(384),
                    transforms.ToTensor(),
                    transforms.Normalize(MEAN, STD),
                ]
            )

    def __len__(self) -> int:
        return len(self.rows)

    def __getitem__(self, index: int):
        row = self.rows[index]
        image = Image.open(row["path"]).convert("RGB")
        return self.transform(image), self.indices[row["label"]]


def split_rows(rows: list[dict], seed: int) -> tuple[list[dict], list[dict]]:
    train = [row for row in rows if row.get("split") == "train"]
    val = [row for row in rows if row.get("split") == "test"]
    rest = [row for row in rows if row.get("split") not in {"train", "test"}]
    by_label: dict[str, list[dict]] = defaultdict(list)
    for row in rest:
        by_label[row["label"]].append(row)
    rng = random.Random(seed)
    for group in by_label.values():
        rng.shuffle(group)
        if len(group) < 2:
            train.extend(group)
            continue
        hold = max(1, int(round(len(group) * 0.15)))
        hold = min(hold, len(group) - 1)
        val.extend(group[:hold])
        train.extend(group[hold:])
    return train, val


def load_rows() -> list[dict]:
    path = DATA / "manifest.csv"
    with path.open() as handle:
        return list(csv.DictReader(handle))


def make_loader(rows, indices, train: bool, batch: int, workers: int):
    dataset = LeafSet(rows, indices, train)
    sampler = None
    shuffle = not train
    if train:
        counts: dict[str, int] = defaultdict(int)
        for row in rows:
            counts[row["label"]] += 1
        weights = [1.0 / counts[row["label"]] for row in rows]
        sampler = WeightedRandomSampler(weights, len(weights), replacement=True)
        shuffle = False
    return DataLoader(
        dataset,
        batch_size=batch,
        shuffle=shuffle,
        sampler=sampler,
        num_workers=workers,
        pin_memory=True,
        persistent_workers=workers > 0,
    )


def build(num_classes: int) -> nn.Module:
    weights = models.EfficientNet_V2_S_Weights.IMAGENET1K_V1
    model = models.efficientnet_v2_s(weights=weights)
    model.classifier[1] = nn.Linear(model.classifier[1].in_features, num_classes)
    return model


def run_epoch(model, loader, criterion, optimizer, device, train: bool):
    model.train(train)
    total = 0
    correct = 0
    loss_sum = 0.0
    scaler = run_epoch.scaler
    for images, targets in loader:
        images = images.to(device, non_blocking=True)
        targets = targets.to(device, non_blocking=True)
        with torch.set_grad_enabled(train):
            with torch.autocast(device_type="cuda", dtype=torch.float16):
                logits = model(images)
                loss = criterion(logits, targets)
            if train:
                optimizer.zero_grad(set_to_none=True)
                scaler.scale(loss).backward()
                scaler.step(optimizer)
                scaler.update()
        total += targets.size(0)
        correct += (logits.argmax(1) == targets).sum().item()
        loss_sum += loss.item() * targets.size(0)
    return loss_sum / max(total, 1), correct / max(total, 1)


run_epoch.scaler = None


def fit(batch: int, epochs: int, workers: int, seed: int) -> None:
    rows = load_rows()
    labels = json.loads((DATA / "classes.json").read_text())
    indices = {label: index for index, label in enumerate(labels)}
    train_rows, val_rows = split_rows(rows, seed)
    device = torch.device("cuda")
    model = build(len(labels)).to(device)
    train_loader = make_loader(train_rows, indices, True, batch, workers)
    val_loader = make_loader(val_rows, indices, False, batch, workers)
    criterion = nn.CrossEntropyLoss(label_smoothing=0.1)
    optimizer = torch.optim.AdamW(model.parameters(), lr=3e-4, weight_decay=1e-4)
    schedule = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)
    run_epoch.scaler = torch.amp.GradScaler("cuda")
    out = ROOT / "runs"
    out.mkdir(parents=True, exist_ok=True)
    best = 0.0
    history = []
    for epoch in range(1, epochs + 1):
        train_loss, train_acc = run_epoch(model, train_loader, criterion, optimizer, device, True)
        val_loss, val_acc = run_epoch(model, val_loader, criterion, optimizer, device, False)
        schedule.step()
        history.append(
            {
                "epoch": epoch,
                "train_loss": train_loss,
                "train_acc": train_acc,
                "val_loss": val_loss,
                "val_acc": val_acc,
            }
        )
        print(
            f"epoch {epoch} train {train_acc:.3f} val {val_acc:.3f} loss {val_loss:.3f}",
            flush=True,
        )
        if val_acc >= best:
            best = val_acc
            torch.save(
                {
                    "model": model.state_dict(),
                    "classes": labels,
                    "image_size": 384,
                    "mean": MEAN,
                    "std": STD,
                    "arch": "efficientnet_v2_s",
                    "val_acc": val_acc,
                },
                out / "efficientnetv2_s_plant.pt",
            )
        (out / "history.json").write_text(json.dumps(history, indent=2) + "\n")
    print(f"best val accuracy {best:.3f}", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--epochs", type=int, default=8)
    parser.add_argument("--batch", type=int, default=32)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    if not torch.cuda.is_available():
        raise SystemExit("CUDA is not available")
    batch = args.batch
    while batch >= 8:
        try:
            fit(batch, args.epochs, args.workers, args.seed)
            return
        except torch.cuda.OutOfMemoryError:
            torch.cuda.empty_cache()
            batch //= 2
            print(f"out of memory, retrying with batch {batch}", flush=True)
    raise SystemExit("batch 8 still does not fit")


if __name__ == "__main__":
    main()
