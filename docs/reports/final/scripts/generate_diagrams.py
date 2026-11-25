#!/usr/bin/env python3
"""Generate architecture, flow, and results diagrams for the Fam-AI final report."""

from __future__ import annotations

from pathlib import Path
import json

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

OUT = Path(__file__).resolve().parents[1] / "diagrams"
OUT.mkdir(parents=True, exist_ok=True)

# Soft academic palette (not purple-on-white / cream-terracotta defaults)
C_PHONE = "#1B4F72"
C_PI = "#1A5276"
C_KB = "#196F3D"
C_TRAIN = "#7D3C98"
C_BOX = "#EBF5FB"
C_EDGE = "#2C3E50"
C_ACCENT = "#117A65"
C_WARN = "#B9770E"
C_LIGHT = "#F8F9F9"


def _box(ax, x, y, w, h, text, fc=C_BOX, ec=C_EDGE, fontsize=9, weight="normal", tc=C_EDGE):
    patch = FancyBboxPatch(
        (x, y), w, h,
        boxstyle="round,pad=0.02,rounding_size=0.08",
        linewidth=1.4, facecolor=fc, edgecolor=ec,
    )
    ax.add_patch(patch)
    ax.text(x + w / 2, y + h / 2, text, ha="center", va="center",
            fontsize=fontsize, fontweight=weight, color=tc, wrap=True,
            linespacing=1.25)
    return patch


def _arrow(ax, x1, y1, x2, y2, color=C_EDGE, style="-|>"):
    ax.add_patch(FancyArrowPatch(
        (x1, y1), (x2, y2),
        arrowstyle=style, mutation_scale=14,
        linewidth=1.3, color=color,
    ))


def architecture_diagram():
    fig, ax = plt.subplots(figsize=(12.5, 8.2), dpi=160)
    ax.set_xlim(0, 12.5)
    ax.set_ylim(0, 8.2)
    ax.axis("off")
    ax.set_title("Fam-AI System Architecture", fontsize=16, fontweight="bold",
                 color=C_EDGE, pad=12)

    # Phone column
    ax.add_patch(FancyBboxPatch((0.3, 0.4), 3.8, 7.4, boxstyle="round,pad=0.02,rounding_size=0.1",
                                linewidth=1.6, facecolor="#EAF2F8", edgecolor=C_PHONE))
    ax.text(2.2, 7.5, "Android Phone", ha="center", fontsize=12, fontweight="bold", color=C_PHONE)

    _box(ax, 0.55, 6.55, 3.3, 0.7, "UI (Compose)\nCapture · Result · Advice · History · Settings",
         fc="#D4E6F1", ec=C_PHONE, fontsize=8)
    _box(ax, 0.55, 5.5, 3.3, 0.7, "ImagePrep\nJPEG · long side 768 · q80 · sha256",
         fc="#D4E6F1", ec=C_PHONE, fontsize=8)
    _box(ax, 0.55, 4.35, 3.3, 0.85, "LeafModel (LiteRT)\nEfficientNetV2-S float32 TFLite\n38 classes · 384 input",
         fc="#AED6F1", ec=C_PHONE, fontsize=8, weight="bold")
    _box(ax, 0.55, 3.2, 3.3, 0.85, "PhoneVision (LiteRT-LM)\nGemma 4 E4B (~3.7 GB)\noptional on-device LLM",
         fc="#AED6F1", ec=C_PHONE, fontsize=8)
    _box(ax, 0.55, 2.15, 3.3, 0.7, "PiClient (OkHttp)\nBearer token · private host only",
         fc="#D4E6F1", ec=C_PHONE, fontsize=8)
    _box(ax, 0.55, 1.0, 3.3, 0.8, "Room Audit DB + Encrypted Prefs\nfam_ai_audit.db · WAL · insert-only",
         fc="#D5F5E3", ec=C_KB, fontsize=8)

    # Network
    ax.annotate("", xy=(7.0, 4.0), xytext=(4.2, 2.5),
                arrowprops=dict(arrowstyle="<->", color=C_ACCENT, lw=2.0))
    ax.text(5.55, 3.55, "Private Wi-Fi\n192.168.4.1:8080\nBearer token",
            ha="center", va="center", fontsize=8, color=C_ACCENT, fontweight="bold",
            bbox=dict(boxstyle="round,pad=0.3", fc="#E8F8F5", ec=C_ACCENT))

    # Pi column
    ax.add_patch(FancyBboxPatch((7.0, 0.4), 5.1, 7.4, boxstyle="round,pad=0.02,rounding_size=0.1",
                                linewidth=1.6, facecolor="#E8F6F3", edgecolor=C_ACCENT))
    ax.text(9.55, 7.5, "Raspberry Pi 5 Field Box", ha="center", fontsize=12,
            fontweight="bold", color=C_ACCENT)

    _box(ax, 7.25, 6.5, 4.6, 0.7, "FastAPI / uvicorn  ·  /v1/health · diagnose · audit · language-pack",
         fc="#D5F5E3", ec=C_ACCENT, fontsize=8)
    _box(ax, 7.25, 5.35, 2.2, 0.85, "Vision factory\nmock | cpu | hailo",
         fc="#A9DFBF", ec=C_ACCENT, fontsize=8, weight="bold")
    _box(ax, 9.55, 5.35, 2.3, 0.85, "LLM factory\nLlama 3.2 1B PTE\nor template",
         fc="#A9DFBF", ec=C_ACCENT, fontsize=8, weight="bold")
    _box(ax, 7.25, 4.15, 4.6, 0.85, "EfficientNetV2-S int8 384 (ExecuTorch .pte)\noptional Hailo HEF · logits = kb/labels.json (30)",
         fc="#D5F5E3", ec=C_ACCENT, fontsize=8)
    _box(ax, 7.25, 3.0, 4.6, 0.8, "KnowledgeBase\nlabels · treatments · regions · packs · advisory prompt",
         fc="#FCF3CF", ec=C_WARN, fontsize=8)
    _box(ax, 7.25, 1.85, 4.6, 0.7, "Security: private bind · private clients · 10/min/device · token 0600",
         fc="#FADBD8", ec="#922B21", fontsize=8)
    _box(ax, 7.25, 0.7, 4.6, 0.8, "Audit SQLite /var/lib/fam-ai/audit.db\nhash only · insert-only triggers · CSV export",
         fc="#D5F5E3", ec=C_ACCENT, fontsize=8)

    fig.tight_layout()
    path = OUT / "system_architecture.png"
    fig.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print("wrote", path)
    return path


def diagnosis_flow_diagram():
    fig, ax = plt.subplots(figsize=(11.5, 9.5), dpi=160)
    ax.set_xlim(0, 11.5)
    ax.set_ylim(0, 9.5)
    ax.axis("off")
    ax.set_title("Diagnosis Data / Process Flow", fontsize=16, fontweight="bold",
                 color=C_EDGE, pad=10)

    steps = [
        (3.5, 8.6, 4.5, 0.6, "1. Capture / gallery photo", "#D4E6F1", C_PHONE),
        (3.5, 7.7, 4.5, 0.6, "2. ImagePrep: EXIF · scale 768 · JPEG q80 · sha256 · lesion heuristic",
         "#D4E6F1", C_PHONE),
        (3.5, 6.8, 4.5, 0.6, "3. LeafModel.predict (bundled EfficientNet, always)",
         "#AED6F1", C_PHONE),
        (3.5, 5.7, 4.5, 0.7, "4. Mode = Pi and health OK?\nYes → POST /v1/diagnose   No → local path",
         "#FCF3CF", C_WARN),
    ]
    for x, y, w, h, t, fc, ec in steps:
        _box(ax, x, y, w, h, t, fc=fc, ec=ec, fontsize=8.5)
        if y < 8.5:
            _arrow(ax, 5.75, y + h + 0.08, 5.75, y + 0.92)

    # Branch Pi
    _box(ax, 0.4, 3.9, 4.8, 1.4,
         "Pi path\nVision: mock / ExecuTorch CPU / Hailo\nSeverity from lesion or confidence\nLlama rewrite (≤256 tokens) or template\nAudit row · advice_language",
         fc="#D5F5E3", ec=C_ACCENT, fontsize=8)
    # Branch local
    _box(ax, 6.3, 3.9, 4.8, 1.4,
         "Local path\nPrefer Gemma 4 if downloaded\nElse bundled EfficientNet + LeafCare / KB notes\nBackend named on result screen\nRoom history row",
         fc="#EAF2F8", ec=C_PHONE, fontsize=8)

    _arrow(ax, 4.5, 5.7, 2.8, 5.35)
    _arrow(ax, 7.0, 5.7, 8.7, 5.35)

    _box(ax, 2.5, 2.4, 6.5, 0.9,
         "5. Result: label · severity (low/med/high) · confidence\nAdvice: summary · do now · treatment · prevention · seek help",
         fc="#D5F5E3", ec=C_KB, fontsize=9, weight="bold")
    _arrow(ax, 2.8, 3.9, 4.5, 3.35)
    _arrow(ax, 8.7, 3.9, 7.0, 3.35)

    _box(ax, 2.5, 1.1, 6.5, 0.8,
         "6. Optional: Listen (TTS) · History · consented share queue",
         fc="#F8F9F9", ec=C_EDGE, fontsize=9)
    _arrow(ax, 5.75, 2.4, 5.75, 1.95)

    ax.text(5.75, 0.35,
            "Image bytes stay off the Pi audit log; phone stores thumbnail path + hash.",
            ha="center", fontsize=8, style="italic", color="#566573")

    fig.tight_layout()
    path = OUT / "diagnosis_flow.png"
    fig.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print("wrote", path)
    return path


def training_pipeline_diagram():
    fig, ax = plt.subplots(figsize=(12, 5.8), dpi=160)
    ax.set_xlim(0, 12)
    ax.set_ylim(0, 5.8)
    ax.axis("off")
    ax.set_title("Training & Export Pipeline", fontsize=15, fontweight="bold", color=C_EDGE)

    boxes = [
        (0.3, 3.5, 2.4, 1.5, "Datasets\nPlantVillage, PlantDoc,\ncassava, CCMT, rice,\nsoybean, multicrop,\nPaddy Doctor", "#E8DAEF", C_TRAIN),
        (3.0, 3.5, 2.4, 1.5, "prepare_manifest\nclass maps · splits\n119,796 images\n103 classes", "#D6EAF8", C_PHONE),
        (5.7, 3.5, 2.4, 1.5, "train_efficientnet\nEfficientNetV2-S\n384 · AMP · AdamW\nbest val acc 0.958", "#D5F5E3", C_ACCENT),
        (8.4, 3.5, 3.3, 1.5, "Export\nTFLite float32 (phone)\nint8 ExecuTorch .pte (Pi)\noptional Hailo HEF", "#FCF3CF", C_WARN),
    ]
    for i, (x, y, w, h, t, fc, ec) in enumerate(boxes):
        _box(ax, x, y, w, h, t, fc=fc, ec=ec, fontsize=8)
        if i < len(boxes) - 1:
            _arrow(ax, x + w + 0.05, y + h / 2, boxes[i + 1][0] - 0.05, y + h / 2)

    _box(ax, 0.3, 0.7, 3.5, 1.8,
         "Augmentation / optimization\n• resize 416 → random crop 384\n• flip + color jitter\n• class-balanced sampler\n• label smoothing 0.1\n• AMP float16 + OOM batch halve",
         fc="#F5EEF8", ec=C_TRAIN, fontsize=8)
    _box(ax, 4.2, 0.7, 3.5, 1.8,
         "Class-list reality\n• train: 103-way (alphabetized)\n• advice KB: 30 labels\n• phone asset: 38-class TFLite\nNext step: align export heads",
         fc="#FDEBD0", ec=C_WARN, fontsize=8)
    _box(ax, 8.1, 0.7, 3.5, 1.8,
         "Hardware used\n• Train: NVIDIA RTX 4060 Ti 16 GB\n• Deploy Pi: Raspberry Pi 5 Arm CPU\n  (+ optional Hailo HAT)\n• Phone: LiteRT / XNNPACK / GPU try",
         fc="#EAF2F8", ec=C_PHONE, fontsize=8)

    fig.tight_layout()
    path = OUT / "training_pipeline.png"
    fig.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print("wrote", path)
    return path


def results_chart():
    root = Path(__file__).resolve().parents[4]
    hist_path = root / "train" / "runs" / "history.json"
    history = json.loads(hist_path.read_text())
    epochs = [h["epoch"] for h in history]
    train_acc = [h["train_acc"] for h in history]
    val_acc = [h["val_acc"] for h in history]
    val_loss = [h["val_loss"] for h in history]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(11.5, 4.2), dpi=160)
    ax1.plot(epochs, train_acc, "o-", color=C_PHONE, label="Train accuracy", lw=2)
    ax1.plot(epochs, val_acc, "s-", color=C_ACCENT, label="Validation accuracy", lw=2)
    ax1.axhline(0.9578, color=C_WARN, ls="--", lw=1, label="Best val 0.958 (epoch 6)")
    ax1.set_xlabel("Epoch")
    ax1.set_ylabel("Accuracy")
    ax1.set_title("EfficientNetV2-S (103-class) Accuracy")
    ax1.set_ylim(0.82, 1.01)
    ax1.grid(True, alpha=0.3)
    ax1.legend(fontsize=8)

    ax2.plot(epochs, val_loss, "D-", color="#922B21", label="Validation loss", lw=2)
    ax2.set_xlabel("Epoch")
    ax2.set_ylabel("Loss")
    ax2.set_title("Validation Loss")
    ax2.grid(True, alpha=0.3)
    ax2.legend(fontsize=8)

    fig.suptitle("Training Results — 98,693 train / 21,103 val images", fontsize=12,
                 fontweight="bold", color=C_EDGE)
    fig.tight_layout()
    path = OUT / "training_results.png"
    fig.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print("wrote", path)
    return path


def dataset_chart():
    root = Path(__file__).resolve().parents[4]
    summary = json.loads((root / "train" / "data" / "summary.json").read_text())
    by_source = summary["by_source"]
    labels = list(by_source.keys())
    values = list(by_source.values())

    fig, ax = plt.subplots(figsize=(10, 4.5), dpi=160)
    colors = plt.cm.Set2.colors
    bars = ax.barh(labels, values, color=colors[: len(labels)], edgecolor=C_EDGE, linewidth=0.6)
    ax.set_xlabel("Images")
    ax.set_title(f"Training Manifest by Source — {summary['images']:,} images, {summary['classes']} classes")
    ax.invert_yaxis()
    for bar, v in zip(bars, values):
        ax.text(v + 200, bar.get_y() + bar.get_height() / 2, f"{v:,}", va="center", fontsize=8)
    ax.set_xlim(0, max(values) * 1.18)
    fig.tight_layout()
    path = OUT / "dataset_sources.png"
    fig.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print("wrote", path)
    return path


def write_mermaid_sources():
    arch = """```mermaid
flowchart LR
  subgraph Phone["Android Phone"]
    UI[Compose UI]
    Prep[ImagePrep]
    Leaf[LeafModel TFLite 38]
    Gemma[Gemma 4 LiteRT-LM]
    PiC[PiClient]
    Room[(Room Audit)]
  end
  subgraph Pi["Raspberry Pi 5"]
    API[FastAPI]
    Vis[Vision factory]
    LLM[Llama 3.2 1B / template]
    KB[(kb labels treatments packs)]
    Audit[(audit.db)]
  end
  UI --> Prep --> Leaf
  Prep --> PiC
  PiC -->|private Wi-Fi + token| API
  API --> Vis --> LLM
  LLM --> KB
  API --> Audit
  Leaf --> Gemma
  Leaf --> Room
```"""
    flow = """```mermaid
flowchart TD
  A[Capture / Gallery] --> B[ImagePrep JPEG 768 q80]
  B --> C[Bundled EfficientNet predict]
  C --> D{Pi mode and health OK?}
  D -->|Yes| E[POST /v1/diagnose]
  E --> F[Vision mock/cpu/hailo]
  F --> G[Severity]
  G --> H[Llama or template advice]
  H --> I[Audit + response]
  D -->|No| J{Gemma downloaded?}
  J -->|Yes| K[On-device Gemma 4]
  J -->|No| L[EfficientNet + LeafCare/KB]
  I --> M[Result + Advice screens]
  K --> M
  L --> M
  M --> N[Listen / History / optional share]
```"""
    (OUT / "system_architecture.mmd").write_text(arch + "\n")
    (OUT / "diagnosis_flow.mmd").write_text(flow + "\n")
    print("wrote mermaid sources")


if __name__ == "__main__":
    architecture_diagram()
    diagnosis_flow_diagram()
    training_pipeline_diagram()
    results_chart()
    dataset_chart()
    write_mermaid_sources()
    print("all diagrams ready in", OUT)
