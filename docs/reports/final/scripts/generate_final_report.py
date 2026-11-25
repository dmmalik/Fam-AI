#!/usr/bin/env python3
"""Generate Fam-AI Final Project Report as DOCX and PDF with embedded diagrams."""

from __future__ import annotations

import json
from datetime import date
from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor, Cm

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import inch, cm
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Image, PageBreak, Table, TableStyle,
    KeepTogether, ListFlowable, ListItem, HRFlowable,
)

# .../Fam-AI/docs/reports/final/scripts/this_file.py → parents[4] = Fam-AI
ROOT = Path(__file__).resolve().parents[4]
OUT = Path(__file__).resolve().parents[1]
DIAG = OUT / "diagrams"
TODAY = date(2026, 11, 25)

HISTORY = json.loads((ROOT / "train" / "runs" / "history.json").read_text())
PLANTDOC = json.loads((ROOT / "train" / "runs" / "plantdoc_test.json").read_text())
SUMMARY = json.loads((ROOT / "train" / "data" / "summary.json").read_text())
LABELS = json.loads((ROOT / "kb" / "labels.json").read_text())
LANGS = json.loads((ROOT / "kb" / "languages.json").read_text())
LEAF_META = json.loads((ROOT / "android" / "app" / "src" / "main" / "assets" / "leaf" / "leaf_meta.json").read_text())


# ---------------------------------------------------------------------------
# Content helpers (shared facts)
# ---------------------------------------------------------------------------

def best_val():
    return max(HISTORY, key=lambda h: h["val_acc"])


def dataset_rows():
    return [[k, f"{v:,}"] for k, v in SUMMARY["by_source"].items()]


def epoch_rows():
    rows = [["Epoch", "Train Acc", "Val Acc", "Val Loss"]]
    for h in HISTORY:
        rows.append([
            str(h["epoch"]),
            f"{h['train_acc']:.3f}",
            f"{h['val_acc']:.3f}",
            f"{h['val_loss']:.3f}",
        ])
    return rows


def label_ids():
    return [x["id"] for x in LABELS]


# ===========================================================================
# DOCX
# ===========================================================================

def set_doc_styles(doc: Document):
    styles = doc.styles
    normal = styles["Normal"]
    normal.font.name = "Calibri"
    normal.font.size = Pt(11)
    normal._element.rPr.rFonts.set(qn("w:eastAsia"), "Calibri")
    pf = normal.paragraph_format
    pf.space_after = Pt(8)
    pf.line_spacing_rule = WD_LINE_SPACING.SINGLE

    for level in range(1, 4):
        style = styles[f"Heading {level}"]
        style.font.color.rgb = RGBColor(0x1B, 0x4F, 0x72)
        style.font.name = "Calibri"
        style.font.bold = True


def add_heading(doc, text, level=1):
    return doc.add_heading(text, level=level)


def add_para(doc, text, bold=False, italic=False, center=False, size=11):
    p = doc.add_paragraph()
    run = p.add_run(text)
    run.bold = bold
    run.italic = italic
    run.font.size = Pt(size)
    run.font.name = "Calibri"
    if center:
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    else:
        p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    return p


def add_bullets(doc, items):
    for item in items:
        p = doc.add_paragraph(item, style="List Bullet")
        p.paragraph_format.space_after = Pt(2)


def add_table(doc, rows, col_widths=None):
    table = doc.add_table(rows=len(rows), cols=len(rows[0]))
    table.style = "Table Grid"
    for i, row in enumerate(rows):
        for j, cell in enumerate(row):
            table.rows[i].cells[j].text = str(cell)
            for para in table.rows[i].cells[j].paragraphs:
                for run in para.runs:
                    run.font.size = Pt(9)
                    run.font.name = "Calibri"
                    if i == 0:
                        run.bold = True
    if col_widths:
        for row in table.rows:
            for idx, w in enumerate(col_widths):
                row.cells[idx].width = Inches(w)
    doc.add_paragraph()
    return table


def add_image(doc, path: Path, width=6.3, caption=None):
    if not path.exists():
        add_para(doc, f"[Missing diagram: {path.name}]", italic=True)
        return
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run()
    run.add_picture(str(path), width=Inches(width))
    if caption:
        cap = doc.add_paragraph()
        cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = cap.add_run(caption)
        r.italic = True
        r.font.size = Pt(9)
        r.font.color.rgb = RGBColor(0x56, 0x65, 0x73)


def build_docx(path: Path):
    doc = Document()
    set_doc_styles(doc)
    section = doc.sections[0]
    section.top_margin = Cm(2.0)
    section.bottom_margin = Cm(2.0)
    section.left_margin = Cm(2.2)
    section.right_margin = Cm(2.2)

    # ----- Cover -----
    for _ in range(3):
        doc.add_paragraph()
    add_para(doc, "FINAL PROJECT REPORT", bold=True, center=True, size=14)
    doc.add_paragraph()
    add_para(doc, "Fam-AI", bold=True, center=True, size=28)
    add_para(
        doc,
        "Offline-Capable Leaf Disease and Pest Diagnosis\nfor Farmers using Android and Raspberry Pi",
        center=True, size=14,
    )
    doc.add_paragraph()
    add_para(doc, "Academic / Technical Project Report", italic=True, center=True, size=12)
    doc.add_paragraph()
    bv = best_val()
    add_para(
        doc,
        f"Repository: Fam-AI\nDate: {TODAY.isoformat()}\n"
        f"Vision: EfficientNetV2-S · Advice: Llama 3.2 1B / Gemma 4\n"
        f"Best validation accuracy (103-class): {bv['val_acc']:.3f} (epoch {bv['epoch']})",
        center=True, size=11,
    )
    doc.add_page_break()

    # ----- Abstract -----
    add_heading(doc, "Abstract", 1)
    add_para(
        doc,
        "Fam-AI is a field leaf-photo diagnosis system for farmers. An Android phone captures "
        "or selects a leaf image; a Raspberry Pi 5 on a private hotspot can classify the image "
        "and return structured agricultural advice without sending the photo to the public internet. "
        "When the Pi is unavailable, the phone uses a bundled EfficientNetV2-S TensorFlow Lite "
        "classifier and, optionally, a downloaded Gemma 4 on-device language model. On the Pi, "
        "vision is EfficientNetV2-S (int8, 384 input) via ExecuTorch (or an optional Hailo HAT), "
        "and advice is rewritten by Llama 3.2 1B Instruct (capped at 256 new tokens) with a "
        "deterministic template fallback from a curated knowledge base. The training workspace "
        f"produced a 103-class EfficientNetV2-S checkpoint on {SUMMARY['images']:,} images with "
        f"best validation accuracy {best_val()['val_acc']:.3f}. The shipped phone asset remains "
        f"a 38-class float32 TFLite model ({LEAF_META['bytes']:,} bytes). The system supports "
        f"{len(LANGS)} languages, three agro-climatic regions, insert-only audit logging, and "
        "safety constraints that forbid inventing spray doses. This report documents methodology, "
        "architecture, implementation, hardware utilization, optimization techniques, evaluation "
        "results, limitations, and future work, grounded in the Fam-AI repository."
    )

    add_heading(doc, "Keywords", 2)
    add_para(
        doc,
        "plant disease classification; edge AI; EfficientNetV2; ExecuTorch; Raspberry Pi; "
        "Android; LiteRT; Gemma; Llama; offline agriculture advisory; quantization",
        italic=True,
    )
    doc.add_page_break()

    # ----- TOC (manual) -----
    add_heading(doc, "Table of Contents", 1)
    toc = [
        "1. Introduction and Problem Statement",
        "2. Objectives and Scope",
        "3. Literature and Related Work",
        "4. System Requirements",
        "5. Methodology",
        "6. System Architecture",
        "7. Design and Implementation",
        "8. Data and Process Flow",
        "9. Hardware Utilization",
        "10. Optimization Techniques",
        "11. Testing, Evaluation, and Results",
        "12. Discussion and Limitations",
        "13. Conclusion and Future Work",
        "14. References",
        "Appendix A — API Summary",
        "Appendix B — Label and Language Inventories",
        "Appendix C — Diagram Sources",
    ]
    for item in toc:
        doc.add_paragraph(item)
    doc.add_page_break()

    # ----- 1 -----
    add_heading(doc, "1. Introduction and Problem Statement", 1)
    add_para(
        doc,
        "Leaf diseases and insect pests are easy to misname from a single glance in the field. "
        "An incorrect spray wastes money and can harm beneficial insects. Extension offices and "
        "Krishi Vigyan Kendras (KVKs) provide authoritative judgment, but they are not present in "
        "every plot when the first lesions appear. Farmers therefore need a portable first check: "
        "one photo, a short likely name, a severity band, and practical next steps that do not "
        "pretend to be a laboratory diagnosis or a prescription."
    )
    add_para(
        doc,
        "Connectivity and privacy constrain that check. Many rural deployments cannot assume "
        "reliable public-cloud inference. Fam-AI therefore keeps diagnosis on a private village "
        "hotspot (Raspberry Pi 5) or entirely on the phone. Photos are not required to leave the "
        "local network; the Pi audit trail stores a SHA-256 of the image, not the image bytes."
    )
    add_para(
        doc,
        "The problem addressed by this project is: design, train, and deploy an end-to-end, "
        "offline-capable leaf diagnosis and advisory pipeline for a small set of staple crops "
        "(tomato, potato, cassava, maize, apple) and selected pests, with multilingual advice, "
        "auditable security properties, and clear fallbacks when models or network paths fail."
    )

    # ----- 2 -----
    add_heading(doc, "2. Objectives and Scope", 1)
    add_heading(doc, "2.1 Objectives", 2)
    add_bullets(doc, [
        "Build an Android application (min SDK 26 / Android 8+) for capture, result, advice, history, and settings.",
        "Provide a Raspberry Pi 5 FastAPI service for private-network diagnose, health, audit, and language-pack endpoints.",
        "Fine-tune EfficientNetV2-S for plant leaf classification and export deployable edge formats (TFLite, ExecuTorch PTE, optional Hailo HEF).",
        "Deliver five-part advice (summary, do now, treatment, prevention, seek help) without inventing product names or doses.",
        "Support 24 languages with three built-in note sets (English, Hindi, Swahili) and downloadable packs for other scheduled Indian languages.",
        "Enforce private bind/client ranges, bearer-token authentication, rate limiting, and insert-only audit logs.",
    ])
    add_heading(doc, "2.2 In Scope", 2)
    add_bullets(doc, [
        "Single-label image classification (not object detection) for disease/pest/healthy classes.",
        "Severity via lesion-area heuristic (disease) or confidence bands (pests).",
        "Regions: Indo-Gangetic plains, Peninsular India, East African highlands (static notes; no weather API).",
        "Optional consented sample upload to createmybills.tech for later training (opt-in).",
    ])
    add_heading(doc, "2.3 Out of Scope", 2)
    add_bullets(doc, [
        "Mandatory user accounts for a check; cloud storage of every photo; public language-pack CDN.",
        "Spray calendars, weather feeds, or replacement of an extension visit.",
        "Houseplants (money plant, pothos, jade) — absent from training classes.",
        "Dose recommendation or branded agrochemical prescription.",
    ])

    # ----- 3 -----
    add_heading(doc, "3. Literature and Related Work", 1)
    add_para(
        doc,
        "Plant disease recognition from leaf images has been widely studied using convolutional "
        "networks on datasets such as PlantVillage (laboratory-style images) and PlantDoc "
        "(more field-like images). Transfer learning from ImageNet with EfficientNet-family "
        "backbones is a common strong baseline for compact accuracy on mobile and edge devices. "
        "EfficientNetV2 improves training and inference efficiency relative to earlier EfficientNet variants."
    )
    add_para(
        doc,
        "Edge deployment for agriculture increasingly pairs a phone UI with a local compute node. "
        "Quantization (int8) and runtimes such as TensorFlow Lite / LiteRT and ExecuTorch reduce "
        "model size and latency on Arm CPUs. Optional NPUs such as Hailo accelerate vision while "
        "keeping language models on CPU. On-device LLMs (e.g., Gemma-class models via LiteRT-LM) "
        "enable advisory rewriting without a cloud API, at the cost of a large download."
    )
    add_para(
        doc,
        "Fam-AI is positioned as a systems integration project: curated multilingual knowledge "
        "base + classifier + constrained LLM rewrite + privacy-preserving local networking, "
        "rather than a claim of state-of-the-art accuracy alone. Related commercial and research "
        "apps often require cloud inference; Fam-AI’s explicit design choice is hotspot-first "
        "offline operation with phone-only fallback."
    )

    # ----- 4 -----
    add_heading(doc, "4. System Requirements", 1)
    add_heading(doc, "4.1 Functional Requirements (summary)", 2)
    add_table(doc, [
        ["ID", "Requirement (short)"],
        ["FR-CAP-1/2", "Capture/gallery; optional crop filter (tomato, potato, cassava, maize, apple)"],
        ["FR-DIAG-1/2/3", "Pi diagnose when reachable; else Gemma; else bundled EfficientNet messaging"],
        ["FR-ADV-1/2/3", "Five-part advice; no invented dose; severity low/medium/high"],
        ["FR-LANG-1..5", "24 languages; packs; English-note flag; TTS Listen"],
        ["FR-REG-1", "Three regions with localized notes"],
        ["FR-HIST-1", "On-device history of successful checks"],
        ["FR-SET-1", "Mode, address, encrypted token, language, region"],
        ["FR-MOD-1", "Resumable plant-model download with Wi-Fi/metered gates"],
    ], col_widths=[1.4, 5.0])
    add_heading(doc, "4.2 Non-Functional / Security", 2)
    add_table(doc, [
        ["ID", "Requirement"],
        ["NFR-SEC-1", "Refuse bind 0.0.0.0; refuse non-private clients"],
        ["NFR-SEC-2", "Constant-time bearer token; token file mode 0600"],
        ["NFR-SEC-3", "~10 diagnose calls/minute/device"],
        ["NFR-SEC-4", "Insert-only audit; store image hash, not bytes"],
        ["NFR-OFF-1", "Hotspot diagnosis without public internet"],
        ["NFR-SAFE-1", "Language packs fail closed if incomplete or still English"],
    ], col_widths=[1.4, 5.0])
    add_heading(doc, "4.3 Environment", 2)
    add_bullets(doc, [
        "Phone: Android 8+, Kotlin, Jetpack Compose, CameraX, Room, OkHttp, LiteRT, LiteRT-LM.",
        "Field box: 64-bit Raspberry Pi OS on Pi 5; FastAPI + uvicorn; ExecuTorch aarch64; optional HailoRT.",
        "Training: PyTorch / torchvision on NVIDIA RTX 4060 Ti 16 GB (as documented in train/train_efficientnet.py).",
    ])

    # ----- 5 -----
    add_heading(doc, "5. Methodology", 1)
    add_heading(doc, "5.1 Overall Approach", 2)
    add_para(
        doc,
        "The methodology separates (1) curated advice knowledge, (2) vision classification, and "
        "(3) constrained language rewriting. Classification never invents treatment text; the LLM "
        "is instructed to rewrite from stored notes and to avoid product names and doses "
        "(kb/prompts/advisory.txt). If JSON parsing fails or Hailo vision is active, the service "
        "returns the template note for the predicted label."
    )
    add_heading(doc, "5.2 Dataset Construction", 2)
    add_para(
        doc,
        "Scripts under train/ download and align multiple public sources into one manifest "
        "(manifest.csv, classes.json, summary.json). prepare_manifest.py maps PlantVillage, "
        "PlantDoc, cassava CSV codes, CCMT names, and other folders onto shared string ids. "
        "Official train/test folders are preserved; unlabeled splits use a 15% per-class "
        "validation holdout (seed 42). An insect-pest parquet set was downloaded then excluded "
        "because integer labels were inconsistent across splits."
    )
    add_table(doc, [["Source", "Images"]] + dataset_rows(), col_widths=[3.0, 2.0])
    add_para(
        doc,
        f"Total after filtering: {SUMMARY['images']:,} images, {SUMMARY['classes']} classes. "
        "Four unreadable/ambiguous rows were dropped (filter.log). Cassava download recorded "
        "68 errors (mostly HTTP 429); training used the 21,329 images that arrived."
    )
    add_image(doc, DIAG / "dataset_sources.png", width=6.0,
              caption="Figure 1. Training manifest counts by source.")

    add_heading(doc, "5.3 Model Training", 2)
    add_para(
        doc,
        "Architecture: torchvision EfficientNet_V2_S with ImageNet1K_V1 weights; final linear "
        "layer replaced for 103 classes. Input 384 with ImageNet mean/std. Training augmentation: "
        "resize 416, random resized crop 384 (scale 0.7–1.0), horizontal flip, mild color jitter. "
        "Validation: resize 416, center crop 384. Loss: cross-entropy with label smoothing 0.1. "
        "Optimizer: AdamW (lr 3e-4, weight decay 1e-4) with cosine schedule over 8 epochs. "
        "Class-balanced sampler (weight 1/count). Mixed precision (float16 autocast + GradScaler). "
        "Batch 32 with automatic halving on CUDA OOM down to 8."
    )
    add_heading(doc, "5.4 Export and Deployment Methodology", 2)
    add_bullets(doc, [
        "Phone: export_tflite.py → float32 TFLite (float16 collapsed all predictions to one class in practice).",
        "Pi vision: int8 ExecuTorch PTE, NCHW 1×3×384×384, logits in kb/labels.json order (30 classes for service).",
        "Pi LLM: separate Llama 3.2 1B Instruct PTE; generate/forward capped at 256 new tokens.",
        "Optional Hailo HEF for vision only; Llama never compiled into HEF.",
        "Android APK packages leaf TFLite uncompressed; Gemma is a farmer-initiated ~3.7 GB download.",
    ])
    add_image(doc, DIAG / "training_pipeline.png", width=6.2,
              caption="Figure 2. Training and export pipeline.")

    add_heading(doc, "5.5 Three Class Lists (methodological constraint)", 2)
    add_para(
        doc,
        "The project intentionally maintains three related but not identical class inventories: "
        f"(1) advice knowledge base kb/labels.json — {len(LABELS)} labels with full treatments; "
        f"(2) training classes.json — {SUMMARY['classes']} ids; "
        f"(3) phone assets leaf/classes.txt — {LEAF_META['classes']} classes in the current APK. "
        "Twenty-eight of thirty advice ids appear in the 103-class training list; pest_aphids and "
        "pest_stem_borer have notes but lacked clean image classes in the retained sets. "
        "Methodology requires export alignment before claiming that training accuracy equals "
        "on-device label identity."
    )

    # ----- 6 -----
    add_heading(doc, "6. System Architecture", 1)
    add_para(
        doc,
        "Fam-AI is a two-node architecture: the Android client and an optional Raspberry Pi 5 "
        "field box on a private Wi-Fi hotspot (typical address 192.168.4.1:8080). There is no "
        "mandatory cloud application server. Two SQLite databases exist with different roles: "
        "Pi audit.db (operator CSV export) and phone Room fam_ai_audit.db (farmer history)."
    )
    add_image(doc, DIAG / "system_architecture.png", width=6.4,
              caption="Figure 3. System architecture — phone, private network, and Pi service.")

    add_heading(doc, "6.1 Technology Stack", 2)
    add_bullets(doc, [
        "Android: Kotlin 2.4, AGP 8.13.2, Compose BOM 2024.12.01, CameraX 1.4.1, Room 2.8.5, OkHttp 4.12.0, LiteRT 2.3.2, LiteRT-LM 0.19.1, security-crypto.",
        "Pi: FastAPI ≥0.115, uvicorn ≥0.32, Pillow, NumPy; ExecuTorch runtime installed separately for aarch64.",
        "Training: torch, torchvision, Pillow, pyarrow.",
        "Knowledge: kb/labels.json, treatments.json, regions.json, languages.json, packs/*.json, prompts/advisory.txt.",
    ])
    add_heading(doc, "6.2 Backend Selection Logic", 2)
    add_para(
        doc,
        "pi/farm_ai/vision/factory.py selects mock (missing PTE), cpu (ExecuTorch PTE present), "
        "or hailo (FAM_AI_HAILO set and HailoRT importable). llm/factory.py uses ExecuTorch Llama "
        "or template fallback. The phone DiagnoseRepository always runs LeafModel first; Pi mode "
        "attempts /v1/health (2 s) then /v1/diagnose; failures continue to Gemma then bundled EfficientNet."
    )

    # ----- 7 -----
    add_heading(doc, "7. Design and Implementation", 1)
    add_heading(doc, "7.1 Android Modules", 2)
    add_bullets(doc, [
        "ui/FamNav.kt — welcome/profile, capture, history, settings, result/{id}, advice/{id}.",
        "data/ImagePrep — EXIF rotation, long-side 768, JPEG quality 80 (down to 40 if >2 MB), thumbnail, sha256, lesion color heuristic.",
        "data/LeafModel — LiteRT Interpreter, 4 threads, XNNPACK off; short side 416, center crop 384; ImageNet normalize; top-3 softmax.",
        "data/PhoneVision — Gemma 4 (gemma-4-E4B-it.litertlm); GPU then CPU; top-k 20, top-p 0.8, temperature 0.2.",
        "data/PiClient — private-range host validation; multipart diagnose; language-pack GET.",
        "data/AuditStore / Room — schema v3, WAL, insert-only triggers; history actions diagnose/local_diagnose/demo_diagnose.",
        "EncryptedSharedPreferences for token and farmer profile (AES-256-GCM).",
    ])
    add_heading(doc, "7.2 Pi Service Modules", 2)
    add_bullets(doc, [
        "api/app.py — routes; OpenAPI/Swagger/ReDoc disabled.",
        "api/security.py — private bind/client, constant-time token compare, rate limit.",
        "api/audit.py — insert-only audit table; CSV stream; file mode 0600.",
        "vision/cpu_executorch.py, hailo_backend.py, mock.py, severity.py.",
        "llm/executorch_llama.py, parser.py, prompt.py, fallback.py.",
        "kb/retrieve.py — validates label/treatment completeness and pack integrity at startup.",
    ])
    add_heading(doc, "7.3 Advice and Severity", 2)
    add_para(
        doc,
        "Severity rules are shared conceptually by pi/farm_ai/vision/severity.py and "
        "android/.../Severity.kt. Healthy labels → low. Disease lesion area: <10% low, "
        "10–30% medium, >30% high. Pests (no trusted lesion): confidence <0.60 low, "
        "0.60–0.85 medium, ≥0.85 high. Lesion area on the phone uses a coarse every-other-pixel "
        "color split (green vs yellow/orange/brown/dark); on the Pi it is recomputed on a "
        "512-pixel prepared image copy."
    )
    add_heading(doc, "7.4 Multilingual Design", 2)
    add_para(
        doc,
        f"{len(LANGS)} languages are listed in kb/languages.json. English, Hindi, and Swahili "
        f"are builtin. {len(list((ROOT/'kb'/'packs').glob('*.json')))} packs under kb/packs/ "
        "cover other scheduled Indian languages. Packs are not baked into the APK; the phone "
        "downloads them from the Pi. Loaders reject packs whose lengths differ from English or "
        "whose summaries remain English. Llama is prompted in the selected language; smaller "
        "languages are expected to be more reliable via phone Gemma or translated packs than via "
        "the 1B Pi model alone."
    )

    # ----- 8 -----
    add_heading(doc, "8. Data and Process Flow", 1)
    add_image(doc, DIAG / "diagnosis_flow.png", width=6.0,
              caption="Figure 4. End-to-end diagnosis process flow.")
    add_para(
        doc,
        "Upload constraints: JPEG/PNG only (magic-byte checked on Pi), 2 MB cap. Phone prepares "
        "JPEG inside that budget. Diagnose multipart fields include image, language, region id, "
        "device id (UUID), optional crop, and Authorization bearer token. Response includes "
        "label, severity, backends, model ids/versions, latency, advice object, advice_language, "
        "and advice_source (llama|template)."
    )

    # ----- 9 -----
    add_heading(doc, "9. Hardware Utilization", 1)
    add_heading(doc, "9.1 Training Hardware", 2)
    add_para(
        doc,
        "Fine-tuning ran on an NVIDIA GeForce RTX 4060 Ti with 16 GB VRAM, as noted in "
        "train/train_efficientnet.py. The 17 November 2026 run used batch size 32 with mixed "
        "precision throughout; the OOM batch-halving path did not fire. CPU host RAM and disk "
        "held the multi-source image corpus under train/data/ (gitignored)."
    )
    add_heading(doc, "9.2 Edge Inference — Raspberry Pi 5", 2)
    add_para(
        doc,
        "Production field inference targets a Raspberry Pi 5 running 64-bit Raspberry Pi OS. "
        "Default path uses the Arm CPU through ExecuTorch for both EfficientNetV2-S int8 vision "
        "and Llama 3.2 1B Instruct. An optional Hailo AI HAT can accelerate vision via a .hef; "
        "when Hailo is selected, Llama is not loaded and template advice is returned. CPU-only "
        "install steps are documented in docs/pi-cpu-install.md. The service binds a private "
        "IPv4 address (e.g., hotspot 192.168.4.1), never 0.0.0.0."
    )
    add_heading(doc, "9.3 On-Device Phone Compute", 2)
    add_para(
        doc,
        "The bundled leaf TFLite (~81 MB float32) runs via LiteRT Interpreter on the phone CPU "
        "(4 threads; XNNPACK disabled in code). Gemma 4 (~3.7 GB litertlm) tries GPU then CPU "
        "backends through LiteRT-LM. CameraX uses the device camera when present; gallery path "
        "works without a camera. Cleartext HTTP to private Pi IPs is permitted by network "
        "security config; PiAddress still rejects non-private hosts."
    )
    add_heading(doc, "9.4 Storage and Secrets", 2)
    add_bullets(doc, [
        "Token: /var/lib/fam-ai/token mode 0600; process exits if mode wrong.",
        "Pi audit DB: /var/lib/fam-ai/audit.db mode 0600, WAL.",
        "Phone: EncryptedSharedPreferences for token/profile; Room for history thumbnails paths.",
        "Weights (.pte, .hef, .pt, large Gemma, training photos) excluded from git.",
    ])

    # ----- 10 -----
    add_heading(doc, "10. Optimization Techniques", 1)
    add_heading(doc, "10.1 Model Compression and Runtime", 2)
    add_bullets(doc, [
        "Int8 quantization of EfficientNetV2-S for Pi ExecuTorch PTE (and Hailo HEF path).",
        "Float32 TFLite retained on phone after float16 export failed functionally (single-class collapse).",
        "Separate small vision model vs large LLM; Llama capped at 256 new tokens.",
        "Image upload downscaling (long side 768, quality 80) to cut bandwidth and Pi decode cost.",
        "TFLite assets stored uncompressed (noCompress tflite) for mmap-friendly loading.",
    ])
    add_heading(doc, "10.2 Training Optimizations", 2)
    add_bullets(doc, [
        "ImageNet transfer learning (EfficientNet_V2_S_Weights.IMAGENET1K_V1).",
        "Mixed precision (AMP float16) to fit batch 32 on 16 GB GPU.",
        "Class-balanced sampling to counter severe class imbalance (e.g., cassava_mosaic 13,210 vs soybean_root 10).",
        "Label smoothing 0.1; cosine LR schedule; AdamW weight decay.",
        "Corrupt-image skip (up to six next-row retries) to keep epochs alive.",
        "Checkpoint keep-best on validation accuracy.",
    ])
    add_heading(doc, "10.3 Systems Optimizations", 2)
    add_bullets(doc, [
        "Health check short-circuit (2 s) before diagnose; local fallback avoids hung UI.",
        "Resumable .partial downloads with HTTP range for Gemma and language packs.",
        "Wi-Fi / metered / Data Saver gates to protect farmer mobile data.",
        "Template advice path avoids LLM cost/latency when Hailo is used or JSON is invalid.",
        "Mock vision (deterministic hash→label) enables API testing without weight files.",
        "Parallel ranged download script (fetch_fast.py) for slow Mendeley mirrors.",
    ])

    # ----- 11 -----
    add_heading(doc, "11. Testing, Evaluation, and Results", 1)
    add_heading(doc, "11.1 Training Results (103-class)", 2)
    add_para(
        doc,
        f"Training finished 17 November 2026. Log train/runs/train.log records 98,693 training "
        f"and 21,103 validation images, 103 classes, batch 32. Best validation accuracy "
        f"{best_val()['val_acc']:.4f} at epoch {best_val()['epoch']} "
        f"(checkpoint train/runs/efficientnetv2_s_plant.pt). Epoch 8 ended at "
        f"{HISTORY[-1]['val_acc']:.3f}."
    )
    add_table(doc, epoch_rows(), col_widths=[1.0, 1.5, 1.5, 1.5])
    add_image(doc, DIAG / "training_results.png", width=6.2,
              caption="Figure 5. Training and validation curves for the 103-class run.")

    add_heading(doc, "11.2 Earlier 38-class Checkpoint and PlantDoc", 2)
    add_para(
        doc,
        f"An earlier PlantVillage-aligned run reached best validation accuracy "
        f"{PLANTDOC['checkpoint_val_acc']:.4f} (reported ~0.990). On a PlantDoc-style test split "
        f"for that 38-class checkpoint, accuracy was {PLANTDOC['plantdoc_test']['accuracy']:.3f} "
        f"({PLANTDOC['plantdoc_test']['correct']}/{PLANTDOC['plantdoc_test']['total']}). "
        "The cleaner validation split was much easier than field-style photos. The phone APK "
        f"still ships the 38-class float32 TFLite ({LEAF_META['bytes']:,} bytes; check class "
        f"{LEAF_META['check_class']})."
    )

    add_heading(doc, "11.3 Software Verification", 2)
    add_para(
        doc,
        "Pi pytest suite (pi/tests/): API diagnose body and advice_language, public-client "
        "refusal, bad token, token mode 0600, rate limit, append-only audit CSV, severity "
        "boundaries, five advice keys, Tamil pack auth, English pack not served. Android unit "
        "tests: VisionReplyTest, LeafScoresTest, LeafCareTest, ShareQueueTest, DemoLogicTest. "
        "Gaps (documented in docs/reports/04-verification.md): no automated camera/UI tests, "
        "no hotspot device test, Gemma/TFLite files not loaded in unit tests."
    )

    add_heading(doc, "11.4 Qualitative / Operational Results", 2)
    add_bullets(doc, [
        "Health endpoint reports live vision_backend and llm_backend (mock without weights).",
        "Result screen names the backend that actually ran (cpu, hailo, mock, on-device gemma4, on-device efficientnetv2).",
        "Shared APK: share/Fam-AI.apk; debug build via android/gradlew :app:assembleDebug.",
        "Farmer-facing documentation: START-HERE.md and FARMER_REPORT.md.",
    ])

    # ----- 12 -----
    add_heading(doc, "12. Discussion and Limitations", 1)
    add_para(
        doc,
        "Validation accuracy 0.958 on a mixed 103-class manifest is not interchangeable with "
        "0.990 on the narrower 38-class run, nor with PlantDoc field accuracy 0.682. The "
        "103-class checkpoint is not yet the model loaded by the current APK or by the Pi "
        "service’s 30-logit ExecuTorch contract. Claiming end-to-end field accuracy therefore "
        "requires a completed export and re-evaluation step."
    )
    add_bullets(doc, [
        "Class-list mismatch among train (103), advice (30), and phone (38).",
        "Missing pest image classes for aphids and stem borer despite treatment notes.",
        "Lesion severity is a color heuristic, not a segmentation model.",
        "Llama 1B multilingual quality for low-resource languages is limited; packs/Gemma mitigate.",
        "No automated UI or real hotspot integration tests yet.",
        "Houseplants and many crops outside the advice/training intersection are unsupported.",
        "Optional createmybills sharing requires a build-time share key not stored in git.",
    ])

    # ----- 13 -----
    add_heading(doc, "13. Conclusion and Future Work", 1)
    add_para(
        doc,
        "Fam-AI demonstrates a complete, privacy-aware leaf diagnosis product path: Android UI, "
        "private Pi inference with quantized EfficientNet and constrained Llama rewrite, "
        "on-device EfficientNet/Gemma fallbacks, multilingual knowledge packs, and auditable "
        "security controls. Training produced a strong 103-class EfficientNetV2-S checkpoint "
        "(best val 0.958) on nearly 120k images. The immediate engineering priority is export "
        "alignment so that training metrics, Pi logits, and phone assets refer to one coherent "
        "label contract."
    )
    add_heading(doc, "13.1 Future Work", 2)
    add_bullets(doc, [
        "Export 103-class float32 TFLite into app assets, or train/export a 30-way head matching kb/labels.json for the Pi PTE.",
        "Re-score PlantDoc-style field splits on the wide checkpoint.",
        "Collect/add images for pest_aphids and pest_stem_borer; retry remaining cassava 429 failures.",
        "Device UI tests and a scripted hotspot diagnose smoke test.",
        "Optional houseplant datasets if product scope expands.",
        "Further latency profiling on Pi 5 CPU vs Hailo for vision.",
    ])

    # ----- 14 -----
    add_heading(doc, "14. References", 1)
    refs = [
        "Tan, M. & Le, Q. EfficientNetV2: Smaller Models and Faster Training. ICML 2021.",
        "Hughes, D.P. & Salathé, M. An open access repository of images on plant health. PlantVillage dataset / related publications.",
        "Singh et al. PlantDoc: A Dataset for Visual Plant Disease Detection. (field-style plant images).",
        "Meta Llama 3.2 model card — 1B Instruct.",
        "Google Gemma / LiteRT-LM on-device deployment documentation.",
        "ExecuTorch documentation (PyTorch edge runtime).",
        "TensorFlow Lite / LiteRT interpreter guides.",
        "HailoRT / Hailo Dataflow Compiler documentation (optional NPU path).",
        "Fam-AI repository documents: README.md, PROJECT.md, docs/reports/00–05, pi/EXPORT.md, docs/pi-cpu-install.md, PROJECT_REPORT.md.",
        "Android Jetpack Compose, CameraX, Room, and Security Crypto official guides.",
        "FastAPI and uvicorn documentation.",
    ]
    for i, r in enumerate(refs, 1):
        add_para(doc, f"[{i}] {r}")

    # ----- Appendix A -----
    doc.add_page_break()
    add_heading(doc, "Appendix A — API Summary", 1)
    add_table(doc, [
        ["Method", "Path", "Auth", "Notes"],
        ["GET", "/v1/health", "No", "vision_backend, llm_backend"],
        ["POST", "/v1/diagnose", "Bearer", "JPEG/PNG ≤2 MB; rate limited"],
        ["GET", "/v1/language-pack/{code}", "Bearer", "Not en/hi/sw"],
        ["GET", "/v1/audit", "Bearer", "CSV stream, insert-only log"],
    ], col_widths=[1.0, 2.4, 1.0, 2.2])

    # ----- Appendix B -----
    add_heading(doc, "Appendix B — Label and Language Inventories", 1)
    add_para(doc, "Advice labels (kb/labels.json), in file order:", bold=True)
    add_para(doc, ", ".join(label_ids()), size=9)
    add_para(doc, "Languages (code): " + ", ".join(l["code"] for l in LANGS), size=9)
    add_para(
        doc,
        "Regions: indo_gangetic, peninsular_india, east_african_highlands.",
        size=9,
    )

    # ----- Appendix C -----
    add_heading(doc, "Appendix C — Diagram Sources", 1)
    add_para(
        doc,
        "PNG diagrams were generated by docs/reports/final/scripts/generate_diagrams.py "
        "(matplotlib). Mermaid sources are stored beside the PNGs as "
        "system_architecture.mmd and diagnosis_flow.mmd for reuse in Markdown viewers."
    )
    add_para(
        doc,
        "Report files generated by docs/reports/final/scripts/generate_final_report.py.",
        italic=True,
    )

    doc.save(path)
    print("wrote", path)


# ===========================================================================
# PDF (ReportLab)
# ===========================================================================

def pdf_styles():
    styles = getSampleStyleSheet()
    styles.add(ParagraphStyle(
        name="CoverTitle", parent=styles["Title"], fontSize=26, leading=30,
        textColor=colors.HexColor("#1B4F72"), spaceAfter=12, alignment=TA_CENTER,
    ))
    styles.add(ParagraphStyle(
        name="CoverSub", parent=styles["Normal"], fontSize=13, leading=18,
        alignment=TA_CENTER, textColor=colors.HexColor("#2C3E50"), spaceAfter=8,
    ))
    styles.add(ParagraphStyle(
        name="BodyJust", parent=styles["Normal"], fontSize=10.5, leading=14,
        alignment=TA_JUSTIFY, spaceAfter=8,
    ))
    styles.add(ParagraphStyle(
        name="H1Fam", parent=styles["Heading1"], fontSize=14, leading=18,
        textColor=colors.HexColor("#1B4F72"), spaceBefore=14, spaceAfter=8,
    ))
    styles.add(ParagraphStyle(
        name="H2Fam", parent=styles["Heading2"], fontSize=12, leading=15,
        textColor=colors.HexColor("#117A65"), spaceBefore=10, spaceAfter=6,
    ))
    styles.add(ParagraphStyle(
        name="Caption", parent=styles["Normal"], fontSize=9, leading=11,
        alignment=TA_CENTER, textColor=colors.HexColor("#566573"), spaceAfter=10,
        spaceBefore=4, fontName="Helvetica-Oblique",
    ))
    styles.add(ParagraphStyle(
        name="BulletBody", parent=styles["Normal"], fontSize=10.5, leading=13,
        leftIndent=12, spaceAfter=3,
    ))
    return styles


def p(styles, text, style="BodyJust"):
    return Paragraph(text.replace("\n", "<br/>"), styles[style])


def pdf_table(rows, col_widths):
    data = []
    for i, row in enumerate(rows):
        data.append([Paragraph(str(c), ParagraphStyle(
            "td", fontSize=8.5, leading=11,
            fontName="Helvetica-Bold" if i == 0 else "Helvetica",
        )) for c in row])
    t = Table(data, colWidths=col_widths, repeatRows=1)
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#D4E6F1")),
        ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#85929E")),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 4),
        ("RIGHTPADDING", (0, 0), (-1, -1), 4),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8F9F9")]),
    ]))
    return t


def pdf_image(path: Path, width=6.2 * inch):
    if not path.exists():
        return Paragraph(f"[Missing {path.name}]", ParagraphStyle("m", fontSize=9, textColor=colors.red))
    img = Image(str(path))
    img._restrictSize(width, 5.5 * inch)
    return img


def build_pdf(path: Path):
    styles = pdf_styles()
    story = []
    bv = best_val()

    story.append(Spacer(1, 1.2 * inch))
    story.append(p(styles, "FINAL PROJECT REPORT", "CoverSub"))
    story.append(Spacer(1, 0.2 * inch))
    story.append(p(styles, "Fam-AI", "CoverTitle"))
    story.append(p(
        styles,
        "Offline-Capable Leaf Disease and Pest Diagnosis<br/>for Farmers using Android and Raspberry Pi",
        "CoverSub",
    ))
    story.append(Spacer(1, 0.3 * inch))
    story.append(HRFlowable(width="80%", thickness=1, color=colors.HexColor("#117A65"), spaceBefore=4, spaceAfter=12))
    story.append(p(
        styles,
        f"Date: {TODAY.isoformat()}<br/>"
        f"Vision: EfficientNetV2-S · Advice: Llama 3.2 1B / Gemma 4<br/>"
        f"Best validation accuracy (103-class): {bv['val_acc']:.3f} (epoch {bv['epoch']})",
        "CoverSub",
    ))
    story.append(PageBreak())

    story.append(p(styles, "Abstract", "H1Fam"))
    story.append(p(styles,
        "Fam-AI is a field leaf-photo diagnosis system for farmers. An Android phone captures "
        "or selects a leaf image; a Raspberry Pi 5 on a private hotspot can classify the image "
        "and return structured agricultural advice without sending the photo to the public internet. "
        "When the Pi is unavailable, the phone uses a bundled EfficientNetV2-S TensorFlow Lite "
        "classifier and, optionally, a downloaded Gemma 4 on-device language model. On the Pi, "
        "vision is EfficientNetV2-S (int8, 384 input) via ExecuTorch (or an optional Hailo HAT), "
        "and advice is rewritten by Llama 3.2 1B Instruct (capped at 256 new tokens) with a "
        "deterministic template fallback from a curated knowledge base. The training workspace "
        f"produced a 103-class EfficientNetV2-S checkpoint on {SUMMARY['images']:,} images with "
        f"best validation accuracy {bv['val_acc']:.3f}. The shipped phone asset remains "
        f"a 38-class float32 TFLite model ({LEAF_META['bytes']:,} bytes). The system supports "
        f"{len(LANGS)} languages, three agro-climatic regions, insert-only audit logging, and "
        "safety constraints that forbid inventing spray doses. This report documents methodology, "
        "architecture, implementation, hardware utilization, optimization techniques, evaluation "
        "results, limitations, and future work, grounded in the Fam-AI repository."
    ))
    story.append(p(styles, "<b>Keywords:</b> plant disease classification; edge AI; EfficientNetV2; "
                           "ExecuTorch; Raspberry Pi; Android; LiteRT; Gemma; Llama; quantization.", "BodyJust"))
    story.append(PageBreak())

    story.append(p(styles, "Table of Contents", "H1Fam"))
    for item in [
        "1. Introduction and Problem Statement",
        "2. Objectives and Scope",
        "3. Literature and Related Work",
        "4. System Requirements",
        "5. Methodology",
        "6. System Architecture",
        "7. Design and Implementation",
        "8. Data and Process Flow",
        "9. Hardware Utilization",
        "10. Optimization Techniques",
        "11. Testing, Evaluation, and Results",
        "12. Discussion and Limitations",
        "13. Conclusion and Future Work",
        "14. References",
        "Appendix A–C",
    ]:
        story.append(p(styles, item, "BulletBody"))
    story.append(PageBreak())

    # Sections — condensed but complete for PDF (mirrors DOCX)
    sections = []

    def h1(t): story.append(p(styles, t, "H1Fam"))
    def h2(t): story.append(p(styles, t, "H2Fam"))
    def body(t): story.append(p(styles, t, "BodyJust"))
    def bullets(items):
        for it in items:
            story.append(p(styles, f"• {it}", "BulletBody"))

    h1("1. Introduction and Problem Statement")
    body(
        "Leaf diseases and insect pests are easy to misname from a single glance in the field. "
        "An incorrect spray wastes money and can harm beneficial insects. Extension offices and "
        "KVKs provide authoritative judgment, but they are not present in every plot when the "
        "first lesions appear. Fam-AI addresses the need for a portable first check—one photo, "
        "a short likely name, a severity band, and practical next steps—without requiring public "
        "cloud inference. Diagnosis runs on a private Raspberry Pi 5 hotspot or entirely on the phone. "
        "The Pi audit trail stores a SHA-256 of the image, not the image bytes."
    )

    h1("2. Objectives and Scope")
    h2("2.1 Objectives")
    bullets([
        "Android app (min SDK 26) for capture, result, advice, history, settings.",
        "Raspberry Pi 5 FastAPI service: diagnose, health, audit, language packs.",
        "Fine-tune and export EfficientNetV2-S for edge (TFLite / ExecuTorch / optional Hailo).",
        "Five-part advice without inventing product names or doses.",
        "24 languages; three built-in note sets; downloadable Indian-language packs.",
        "Private networking, bearer tokens, rate limits, insert-only audits.",
    ])
    h2("2.2 Out of Scope")
    bullets([
        "Mandatory accounts; cloud storage of every photo; public pack CDN.",
        "Spray calendars, weather APIs, dose prescriptions, houseplant coverage.",
        "Replacement of extension / KVK visits.",
    ])

    h1("3. Literature and Related Work")
    body(
        "CNN-based leaf disease recognition on PlantVillage and PlantDoc is a mature research "
        "line; ImageNet transfer learning with EfficientNet-family models is a strong compact "
        "baseline. Edge agriculture systems increasingly combine phone UIs with local compute, "
        "int8 quantization, and runtimes such as LiteRT and ExecuTorch. Optional NPUs (Hailo) "
        "accelerate vision while LLMs often remain on CPU. Fam-AI emphasizes systems integration: "
        "curated multilingual KB + classifier + constrained LLM rewrite + privacy-preserving "
        "local networking, rather than cloud-only inference."
    )

    h1("4. System Requirements")
    story.append(pdf_table([
        ["ID", "Requirement (short)"],
        ["FR-CAP / FR-DIAG", "Capture/gallery; Pi diagnose or local Gemma/EfficientNet"],
        ["FR-ADV", "Five-part advice; no invented dose; severity bands"],
        ["FR-LANG / FR-REG", "24 languages; packs; three regions; TTS Listen"],
        ["NFR-SEC / OFF", "Private bind/clients; token 0600; rate limit; hash audit; offline hotspot"],
    ], [1.3 * inch, 5.0 * inch]))
    story.append(Spacer(1, 8))
    body(
        "Environment: Android 8+ (Kotlin/Compose/LiteRT); Pi 5 64-bit OS (FastAPI/ExecuTorch/"
        "optional HailoRT); training on NVIDIA RTX 4060 Ti 16 GB."
    )

    h1("5. Methodology")
    h2("5.1 Approach")
    body(
        "Advice knowledge, vision classification, and constrained LLM rewriting are separated. "
        "The advisory prompt forbids new products/doses. Unusable Llama JSON or Hailo vision "
        "falls back to template notes."
    )
    h2("5.2 Datasets")
    body(
        f"Aligned multi-source manifest: {SUMMARY['images']:,} images, {SUMMARY['classes']} classes "
        "(PlantVillage, soybean, multicrop, PlantDoc, cassava, CCMT, rice-india, Paddy Doctor). "
        "15% per-class val holdout (seed 42) when splits absent."
    )
    story.append(pdf_table([["Source", "Images"]] + dataset_rows(), [3.2 * inch, 2.0 * inch]))
    story.append(Spacer(1, 6))
    story.append(pdf_image(DIAG / "dataset_sources.png"))
    story.append(p(styles, "Figure 1. Training manifest counts by source.", "Caption"))

    h2("5.3 Training")
    body(
        "EfficientNetV2-S ImageNet init; 384 input; AMP; AdamW 3e-4; cosine 8 epochs; "
        "label smoothing 0.1; class-balanced sampler; batch 32 with OOM halving."
    )
    story.append(pdf_image(DIAG / "training_pipeline.png", width=6.3 * inch))
    story.append(p(styles, "Figure 2. Training and export pipeline.", "Caption"))

    h2("5.4 Class-list constraint")
    body(
        f"Advice KB: {len(LABELS)} labels; training: {SUMMARY['classes']}; phone asset: "
        f"{LEAF_META['classes']}. Export alignment is required before equating training accuracy "
        "with on-device labels."
    )

    h1("6. System Architecture")
    body(
        "Two-node design: Android client and optional Pi 5 field box on private Wi-Fi "
        "(192.168.4.1:8080). No mandatory cloud app server. Dual SQLite roles: Pi audit vs phone history."
    )
    story.append(pdf_image(DIAG / "system_architecture.png", width=6.4 * inch))
    story.append(p(styles, "Figure 3. System architecture.", "Caption"))
    bullets([
        "Phone: Compose UI, ImagePrep, LeafModel TFLite, PhoneVision Gemma, PiClient, Room, encrypted prefs.",
        "Pi: FastAPI; vision factory mock|cpu|hailo; Llama/template LLM; KB; security; audit.db.",
    ])

    h1("7. Design and Implementation")
    h2("7.1 Key modules")
    bullets([
        "android/.../ui and data packages: FamNav, DiagnoseRepository, LeafModel, PhoneVision, PiClient, AuditStore.",
        "pi/farm_ai/api, vision, llm, kb packages as described in PROJECT.md / SDD.",
        "Severity: lesion &lt;10%/10–30%/&gt;30% for disease; confidence bands for pests.",
        f"Languages: {len(LANGS)}; packs: {len(list((ROOT/'kb'/'packs').glob('*.json')))}; regions: 3.",
    ])

    h1("8. Data and Process Flow")
    story.append(pdf_image(DIAG / "diagnosis_flow.png", width=5.8 * inch))
    story.append(p(styles, "Figure 4. Diagnosis process flow.", "Caption"))
    body(
        "JPEG/PNG ≤2 MB; multipart diagnose with language, region, device id, optional crop, "
        "bearer token. Response names backends and advice_source."
    )

    h1("9. Hardware Utilization")
    bullets([
        "Training GPU: NVIDIA RTX 4060 Ti 16 GB; batch 32 AMP.",
        "Field box: Raspberry Pi 5 Arm CPU (ExecuTorch); optional Hailo HAT for vision only.",
        "Phone: LiteRT TFLite (~81 MB float32); optional Gemma 4 ~3.7 GB (GPU then CPU).",
        "Secrets/DB on Pi under /var/lib/fam-ai with mode 0600; weights not in git.",
    ])

    h1("10. Optimization Techniques")
    bullets([
        "Int8 PTE/HEF on Pi; float32 TFLite on phone (float16 rejected after collapse bug).",
        "Upload resize 768/q80; Llama ≤256 tokens; template fallback.",
        "Transfer learning, AMP, balanced sampling, label smoothing, cosine LR, best-checkpoint.",
        "Health short-circuit; resumable downloads; Wi-Fi/metered gates; mock backend for tests.",
    ])

    h1("11. Testing, Evaluation, and Results")
    h2("11.1 103-class training")
    body(
        f"Best val accuracy {bv['val_acc']:.4f} at epoch {bv['epoch']}; "
        f"98,693 train / 21,103 val images; batch 32."
    )
    story.append(pdf_table(epoch_rows(), [0.9 * inch, 1.4 * inch, 1.4 * inch, 1.4 * inch]))
    story.append(Spacer(1, 6))
    story.append(pdf_image(DIAG / "training_results.png", width=6.2 * inch))
    story.append(p(styles, "Figure 5. Training curves.", "Caption"))
    h2("11.2 38-class / PlantDoc")
    body(
        f"Earlier checkpoint val ~{PLANTDOC['checkpoint_val_acc']:.3f}; PlantDoc test "
        f"{PLANTDOC['plantdoc_test']['accuracy']:.3f} "
        f"({PLANTDOC['plantdoc_test']['correct']}/{PLANTDOC['plantdoc_test']['total']}). "
        f"APK still ships 38-class TFLite ({LEAF_META['bytes']:,} bytes)."
    )
    h2("11.3 Verification")
    body(
        "Pi pytest covers security, audit, severity, packs; Android unit tests cover parsers and "
        "download gates. Gaps: UI/camera/hotspot automation (see 04-verification.md)."
    )

    h1("12. Discussion and Limitations")
    body(
        "0.958 (103-class val), 0.990 (38-class val), and 0.682 (PlantDoc) measure different "
        "settings. The wide checkpoint is not yet wired into the APK or the 30-logit Pi PTE "
        "contract. Lesion severity is heuristic; aphids/stem borer lack image classes; "
        "low-resource LLM quality needs packs/Gemma."
    )

    h1("13. Conclusion and Future Work")
    body(
        "Fam-AI delivers an offline-capable leaf check with Pi and phone paths, multilingual "
        "advice, and auditable security. Next: align exports to one label contract, re-score "
        "field splits, add missing pest images, and expand device/hotspot tests."
    )

    h1("14. References")
    for i, r in enumerate([
        "Tan &amp; Le. EfficientNetV2. ICML 2021.",
        "PlantVillage / PlantDoc plant disease image datasets.",
        "Meta Llama 3.2 1B Instruct; Google Gemma / LiteRT-LM.",
        "ExecuTorch; TensorFlow Lite / LiteRT; HailoRT docs.",
        "Fam-AI repo: README, PROJECT.md, docs/reports/*, pi/EXPORT.md, PROJECT_REPORT.md.",
        "Android Jetpack; FastAPI documentation.",
    ], 1):
        body(f"[{i}] {r}")

    story.append(PageBreak())
    h1("Appendix A — API Summary")
    story.append(pdf_table([
        ["Method", "Path", "Auth", "Notes"],
        ["GET", "/v1/health", "No", "backends"],
        ["POST", "/v1/diagnose", "Bearer", "≤2 MB image"],
        ["GET", "/v1/language-pack/{code}", "Bearer", "not en/hi/sw"],
        ["GET", "/v1/audit", "Bearer", "CSV"],
    ], [0.9 * inch, 2.3 * inch, 0.9 * inch, 1.8 * inch]))

    h1("Appendix B — Inventories")
    body("<b>Advice labels:</b> " + ", ".join(label_ids()))
    body("<b>Languages:</b> " + ", ".join(l["code"] for l in LANGS))
    body("<b>Regions:</b> indo_gangetic, peninsular_india, east_african_highlands.")

    h1("Appendix C — Diagram Sources")
    body(
        "Generated by docs/reports/final/scripts/generate_diagrams.py and "
        "generate_final_report.py. Mermaid sources: diagrams/*.mmd."
    )

    def _footer(canvas, doc_):
        canvas.saveState()
        canvas.setFont("Helvetica", 8)
        canvas.setFillColor(colors.HexColor("#566573"))
        canvas.drawString(2 * cm, 1.2 * cm, "Fam-AI Final Project Report")
        canvas.drawRightString(A4[0] - 2 * cm, 1.2 * cm, f"Page {doc_.page}")
        canvas.restoreState()

    doc = SimpleDocTemplate(
        str(path), pagesize=A4,
        leftMargin=2 * cm, rightMargin=2 * cm,
        topMargin=1.8 * cm, bottomMargin=1.8 * cm,
        title="Fam-AI Final Project Report",
        author="Fam-AI Project",
    )
    doc.build(story, onFirstPage=_footer, onLaterPages=_footer)
    print("wrote", path)


def main():
    # Ensure diagrams exist
    import generate_diagrams
    generate_diagrams.architecture_diagram()
    generate_diagrams.diagnosis_flow_diagram()
    generate_diagrams.training_pipeline_diagram()
    generate_diagrams.results_chart()
    generate_diagrams.dataset_chart()
    generate_diagrams.write_mermaid_sources()

    docx_path = OUT / "Fam-AI_Final_Project_Report.docx"
    pdf_path = OUT / "Fam-AI_Final_Project_Report.pdf"
    build_docx(docx_path)
    build_pdf(pdf_path)
    print("DONE")
    print(docx_path)
    print(pdf_path)


if __name__ == "__main__":
    main()
