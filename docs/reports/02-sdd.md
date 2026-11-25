# Software design description

This design covers the IDs in [01-srs.md](01-srs.md). Names match [00-baseline.md](00-baseline.md).

## Phone

`android/app/src/main/java/ai/fam/app/ui/FamNav.kt` starts on the photo screen, and it also opens history, settings, result, and advice. That covers FR-CAP-1, FR-HIST-1, and FR-SET-1.

`DiagnoseRepository` prepares a JPEG, then:

1. If mode is Pi, a token is saved, and the address parses, it calls `PiClient.diagnose`.
2. If the box doesn't answer, it uses `PhoneVision` (Gemma 4, backend `on-device gemma4`).
3. The prompt gets the language name and the short notes for each label, from the built-in knowledge base or from a saved pack (FR-DIAG-1, FR-DIAG-2, FR-ADV-2).

`LanguageCatalog` reads `assets/kb/languages.json`. Built-in codes are English, Hindi, and Swahili. `KnowledgeBase` still requires those three keys in the bundled notes (FR-LANG-2).

`LanguagePackDownload` loads `GET /v1/language-pack/{code}` with the saved token. It uses the same mobile-data choice as the plant-model download, but it doesn't wait for the public internet, because the file comes from the field box (FR-LANG-3, NFR-OFF-1). A file is kept only after `LanguagePack.parse` accepts it (NFR-SAFE-1). Remove the file and that language goes back to the English note.

`advice_language` from the box is stored on the history row. `showsEnglishNote` is what puts FR-LANG-4 on the result and advice screens.

`FarmerListen` tries the speech tags from the language list (FR-LANG-5).

## Field box

`pi/farm_ai/api/app.py` builds the service.

- Health, diagnose, audit, and language pack (FR-DIAG-1, FR-LANG-3).
- Private bind and private clients (NFR-SEC-1).
- Token check and the per-device cap (NFR-SEC-2, NFR-SEC-3).
- Insert-only audit, with no image bytes (NFR-SEC-4).

`pi/farm_ai/kb/retrieve.py` loads labels, the three-language notes, and every pack under `kb/packs`. `localized` lays a pack over the note for that language. `advise` asks Llama, using the language name from `kb/languages.json`. Unusable JSON, and the Hailo path, use `fallback_advice` (FR-ADV-1, CON-5).

Vision stays EfficientNetV2-S int8 at 384. `pi/farm_ai/vision/factory.py` picks mock, CPU, or Hailo (CON-2). Severity is `pi/farm_ai/vision/severity.py` (FR-ADV-3).

## Knowledge base

`kb/labels.json` is the class order for export. `kb/treatments.json` and `kb/regions.json` hold English, Hindi, and Swahili. `kb/packs/<code>.json` holds one other language. `kb/prompts/advisory.txt` is the instruction that forbids a new product or dose (FR-ADV-2, CON-4).

The phone assets copy labels, treatments, regions, and the language list. Packs aren't copied into the APK.

## Training

`train/` can download public leaf sets and fine-tune EfficientNetV2-S. Export steps stay in `pi/EXPORT.md`. The checkpoint and the photo archives aren't shared (CON-1).
