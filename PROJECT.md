# Fam-AI project

The phone takes the leaf photo. A Raspberry Pi 5 on a local hotspot does the diagnosis. The phone can also answer on its own with Gemma 4, once that model has been downloaded. The result screen names the backend. A color heuristic is still in the tree, but only the tests use it.

## Pipelines

Vision and advice are two programs.

- Vision is EfficientNetV2-S, int8, 384 input. Class order is the `id` order in `kb/labels.json`. The `.pte` has to use that same order, and so does the `.hef` if a Hailo HAT is attached.
- Advice is Llama 3.2 1B Instruct on the Arm CPU, through ExecuTorch. New tokens stop at 256. Llama never gets compiled into the Hailo HEF.
- `pi/farm_ai/vision/factory.py` picks `mock`, `cpu`, or `hailo`. Hailo is used only when `FAM_AI_HAILO` is set and HailoRT imports. Otherwise the classifier is the ExecuTorch `.pte` on the CPU, or `mock` if that file is missing.
- If Llama comes back with JSON we can't use, the service falls back to the translated note for that label.

Before upload, the phone scales the JPEG so the long side is 768, at quality 80. The Pi accepts JPEG or PNG only, and it caps the file at 2 MB.

## What a diagnosis covers

Crops: tomato, potato, cassava, maize, apple. Pests on the same label list: aphids, fall armyworm, whitefly, stem borer, and tomato spider mites. One classifier. It isn't a detector.

Severity for leaf lesions follows the lesion area: low under 10%, medium from 10% to 30%, high above 30%. Pests use confidence bands.

Languages: English, Hindi, Swahili, and the other scheduled languages of India. English, Hindi, and Swahili notes are built in. A crop-note pack for another language is a file in `kb/packs`. The phone downloads that file from the Pi when the Pi is up. Until the pack is saved, a template answer stays in English and the screen says so. Llama is asked to answer in the language the farmer picked.

Regions, chosen in settings: Indo-Gangetic plains, Peninsular India, East African highlands. There is no weather API. Region notes live in `kb/regions.json`.

## API

The service binds to a private address and drops any client that isn't on a private range.

- `GET /v1/health` reports the live vision and LLM backends. It doesn't need a photo or a token.
- `POST /v1/diagnose` takes the image, language, region, device id, and the bearer token. The body includes `advice_language`, which is the language of the stored note when the text came from the template.
- `GET /v1/language-pack/{code}` needs the token and returns one crop-note pack. English, Hindi, and Swahili aren't served, because they're already in the app.
- `GET /v1/audit` needs the token and streams the audit CSV.

About 10 diagnose calls a minute are allowed per device. The token is compared in constant time. It lives in `/var/lib/fam-ai/token`, mode `0600`, outside the repo. On the phone it sits in encrypted preferences. It isn't hardcoded.

There is one audit log. Rows are insert-only. A row records the time, device id, action, label, severity, backend, model id, model version, language, region, latency, the sha256 of the image, and whether the text came from Llama or the template. Image bytes stay out of the log. The history screen is the successful diagnoses from that log.

## Layout

- `android/` is the phone app.
- `pi/farm_ai/` is the hotspot service.
- `kb/` holds labels, treatments, regions, and prompts.
- `docs/reports/` is the project set that shares one glossary: baseline, requirements, design, manual, verification, and the cover report.
- `docs/pi-cpu-install.md` is the Pi 5 install with no AI HAT.
- `pi/EXPORT.md` is how the `.pte` files are produced.

Weights, the token, the audit database, and the Android SDK aren't part of the tree.
