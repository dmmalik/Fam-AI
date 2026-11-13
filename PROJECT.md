# Fam-AI project

Phone takes the leaf photo. A Raspberry Pi 5 on a local hotspot does the diagnosis. The phone can also answer alone with Gemma 4 when that model has been downloaded. The result screen names the backend. A color heuristic remains in the tree for tests only.

## Pipelines

Vision and advice are two programs.

- Vision is EfficientNetV2-S, int8, 384 input. The class order is the `id` order in `kb/labels.json`. The same order is required in the `.pte` and, when a Hailo HAT is attached, in the `.hef`.
- Advice is Llama 3.2 1B Instruct on the Arm CPU through ExecuTorch. New tokens are capped at 256. Llama is never compiled into the Hailo HEF.
- `pi/farm_ai/vision/factory.py` picks `mock`, `cpu`, or `hailo`. Hailo is used only when `FAM_AI_HAILO` is set and HailoRT imports. Otherwise the classifier is the ExecuTorch `.pte` on the CPU, or `mock` when that file is missing.
- A bad Llama JSON response falls back to the translated note for that label.

The phone scales the JPEG to 768 on the long side, quality 80, before upload. The Pi accepts JPEG or PNG only, with a 2 MB cap.

## What a diagnosis covers

Crops: tomato, potato, cassava, maize, apple. Pests in the same label list: aphids, fall armyworm, whitefly, stem borer, and tomato spider mites. One classifier, not a detector.

Severity for leaf lesions is the lesion area: low under 10%, medium from 10% to 30%, high above 30%. Pests use confidence bands.

Languages: English, Hindi, Swahili, and the other scheduled languages of India. English, Hindi, and Swahili notes are built in. A crop-note pack for another language is a file in `kb/packs`. The phone downloads that file from the Pi when it is present. Until the pack is saved, a template answer stays in English and the screen says so. Llama is asked to answer in the selected language.

Regions, chosen in settings: Indo-Gangetic plains, Peninsular India, East African highlands. There is no weather API. The notes for the region are in `kb/regions.json`.

## API

The service binds to a private address and drops clients that are not on a private range.

- `GET /v1/health` reports the live vision and LLM backends. It does not require a photo or a token.
- `POST /v1/diagnose` takes the image, language, region, device id, and the bearer token. The body includes `advice_language`, which is the language of the stored note when the text came from the template.
- `GET /v1/language-pack/{code}` is token-protected and returns one crop-note pack. English, Hindi, and Swahili are not served, because they are already in the app.
- `GET /v1/audit` is token-protected and streams the audit CSV.

About 10 diagnose calls a minute are allowed per device. The token is compared in constant time. It lives in `/var/lib/fam-ai/token`, mode `0600`, outside the repo. On the phone it is stored in encrypted preferences. It is not hardcoded.

There is one audit log. Rows are insert-only. A row records the time, device id, action, label, severity, backend, model id, model version, language, region, latency, the sha256 of the image, and whether the text came from Llama or the template. Image bytes stay out of the log. The history screen is the successful diagnoses from that log.

## Layout

- `android/` is the phone app.
- `pi/farm_ai/` is the hotspot service.
- `kb/` holds labels, treatments, regions, and prompts.
- `docs/reports/` is the harmonized project set: baseline, requirements, design, manual, verification, and the cover report.
- `docs/pi-cpu-install.md` is the Pi 5 install with no AI HAT.
- `pi/EXPORT.md` is how the `.pte` files are produced.

Weights, the token, the audit database, and the Android SDK are not part of the tree.
