# Fam-AI: A Leaf-Photo Check for the Field

## 1. Title & Project Overview

Fam-AI is a leaf-photo diagnosis app for farmers. The phone takes the photo, or opens one from the gallery. A Raspberry Pi 5 on a local hotspot can classify that photo and write the advice. When the Pi isn't there, the phone can still answer. It already carries a small EfficientNet classifier, and it can also ask a downloaded Gemma 4 model.

What comes back is a field check. It names a likely disease or pest, marks the photo low, medium, or high, and writes what to do today, how to limit the spread, and when to take the leaf to an agriculture officer, a KVK, or an agrovet. The advice doesn't invent a product name or a spray dose. It isn't a laboratory test, and it doesn't stand in for that visit.

Four pieces stay in this repository:

- `android/` is the phone app, in Kotlin and Jetpack Compose. The shared install file is `share/Fam-AI.apk`. It needs Android 8 or newer (min SDK 26).
- `pi/farm_ai/` is the hotspot service. FastAPI, on 64-bit Raspberry Pi OS.
- `kb/` holds the advice labels, treatments, regions, languages, prompts, and the downloadable language packs.
- `train/` holds the scripts that build one image manifest and fine-tune EfficientNetV2-S. Photos, checkpoints, and weight files stay on disk, and they stay out of git.

The vision model we trained in this workspace is EfficientNetV2-S at 384 pixels, with ImageNet normalization. The checkpoint is `train/runs/efficientnetv2_s_plant.pt`. Training finished on 17 November 2026. The log `train/runs/train.log` records 98,693 training images, 21,103 validation images, 103 classes, and batch 32. Best validation accuracy was 0.958, at epoch 6. Epoch 8 finished at 0.953. An earlier, narrower checkpoint is kept as `train/runs/efficientnetv2_s_plant.plantvillage.pt`.

Houseplants such as money plant, pothos, and jade aren't among the 103 training classes in `train/data/classes.json`. A photo of those plants has no trained label to land on.

## 2. Abstract / Executive Summary

A farmer should be able to check a leaf without sending the photo onto the public internet. Fam-AI keeps that check on a private hotspot, and it keeps a second path on the phone for the days the Pi is off.

On the Pi, vision and advice are two programs. Vision is EfficientNetV2-S, int8, 384 input, run through ExecuTorch on the Arm CPU, or through a Hailo HAT when `FAM_AI_HAILO` is set and HailoRT imports. Advice is Llama 3.2 1B Instruct, also through ExecuTorch, capped at 256 new tokens. Llama is never compiled into the Hailo file. If Llama doesn't come back with usable JSON, or if the Hailo path is the one that's running, the service returns the stored note for that label.

On the phone, `DiagnoseRepository` always runs the bundled leaf classifier first. If Settings is in Pi mode, a token is saved, and `GET /v1/health` answers, the photo goes to `POST /v1/diagnose`. If the box doesn't answer, the phone asks Gemma 4 (`gemma-4-E4B-it.litertlm`, backend name `on-device gemma4`) once that file has been downloaded. If Gemma isn't on the phone yet, the bundled EfficientNet result is the answer. The result screen names which path ran.

The training set, the advice set, and the phone file are three different sizes. Mixing them up would make the accuracy number look like it covers the wrong list, so the counts are here on purpose.

- The training manifest `train/data/summary.json`, after a four-file drop, has 119,796 images and 103 classes.
- The advice knowledge base `kb/labels.json` has 30 labels. Those are the only labels with full treatment notes, and they're the class order the Pi classifier has to emit.
- The phone asset `android/app/src/main/assets/leaf/classes.txt` has 38 names. `leaf_meta.json` records 38 classes and an 81,098,868-byte float32 TFLite file. The on-device history row stores the model version `plant-38`. That file was written on 16 November 2026, a day before the 103-class run. The 103-class checkpoint hasn't been copied into the app assets.

The 103-class run used ImageNet-pretrained EfficientNetV2-S, mixed precision, label smoothing of 0.1, and a class-balanced sampler. Best validation accuracy was 0.958. The earlier checkpoint's log, `train/runs/train.plantvillage.log`, records a best validation accuracy of 0.990. `train/runs/plantdoc_test.json` records 38 classes for that narrower model, and 161 of 236 correct on the PlantDoc test split (accuracy about 0.682). The higher validation number came from fewer classes. The field-style PlantDoc split was much harder than the clean validation split.

## 3. Introduction & Objectives

Leaf diseases and pests are easy to misname from a single glance. A wrong spray wastes money, and it can kill the insects that eat the pest. Extension offices and KVKs exist for that judgment. They aren't standing in every plot when the spots first appear. Fam-AI is the check a farmer can run before that visit: one photo, a short name, a severity, and a note that stays inside what we stored.

We set the work up around the limits we were willing to keep.

The photo stays on the hotspot or on the phone. The Pi refuses to bind `0.0.0.0`, and it refuses a client that isn't on a private range. A diagnosis on that network doesn't need the public internet. Language packs come from the Pi, not from a public pack server.

Advice has five parts: a summary, what to do now, treatment, prevention, and when to ask for help. The prompt in `kb/prompts/advisory.txt` tells the language model to write in the selected language, and to leave out any product, dose, or claim the notes don't support. A pack is a translation of the English notes. It doesn't add a new claim.

Severity is one shared rule, in `pi/farm_ai/vision/severity.py` and `android/.../Severity.kt`. A healthy label is low. Other diseases follow lesion area: under 10 percent is low, 10 to 30 percent is medium, above 30 percent is high. Pests have no lesion we trust, so they follow confidence: under 0.60 is low, from 0.60 up to 0.85 is medium, and 0.85 and above is high.

The farmer can leave the crop unset, or set tomato, potato, cassava, maize, or apple. History of successful checks stays on the phone. A Listen button speaks the advice when the phone has a voice for that language. When it doesn't, the writing stays on the screen.

We also wanted a training set wider than the five crops in the original advice list, without throwing away the class ids those notes already use. That's how we ended up with 103 training classes beside 30 advice labels. Section 5 explains the split.

Out of scope, as the requirements put it: accounts as a requirement for a check, cloud storage of every photo, a public pack server, spray calendars, a weather feed, and a replacement for an extension visit. There is an optional upload to `createmybills.tech` for farmers who agree to share a photo for later training. It's off unless the farmer opts in, and the share key isn't in git.

## 4. System Architecture & Tech Stack

The phone and the Pi meet only on a private network. The usual hotspot address is `192.168.4.1`, port `8080`. The phone stores the bearer token in encrypted preferences. On the Pi the token file is `/var/lib/fam-ai/token`, mode `0600`, outside the repository. If that mode is wrong, the process exits. Comparison hashes both strings and uses a constant-time compare.

```
phone (Compose)
  ImagePrep  ->  JPEG, long side 768, quality 80, sha256
  LeafModel  ->  bundled EfficientNetV2-S TFLite (38 classes)
  PiClient   ->  POST /v1/diagnose   (only in Pi mode)
  PhoneVision -> Gemma 4 via LiteRT-LM, if the file is downloaded
  AuditDatabase (Room, SQLite) -> history
        |
        |  private Wi-Fi, bearer token
        v
Pi service (FastAPI, uvicorn)
  security: private bind, private client, 10 diagnoses / minute / device
  vision factory: mock | cpu (ExecuTorch .pte) | hailo (.hef)
  llm factory: executorch Llama 3.2 1B, or template if Hailo or if the file is missing
  KnowledgeBase: kb/labels.json + treatments + regions + packs
  AuditLog: SQLite /var/lib/fam-ai/audit.db
```

There's no separate application server in this tree, and no cloud database either. Two SQLite files exist, and they do different jobs.

The Pi audit database is created by `pi/farm_ai/api/audit.py`. The path defaults to `/var/lib/fam-ai/audit.db`. The table `audit` is insert-only: `BEFORE UPDATE` and `BEFORE DELETE` triggers abort. Journal mode is WAL. Each row stores time, device id, action, label, severity, backend, model id, model version, language, region, latency, the sha256 of the image, and whether the text came from Llama or the template. Image bytes aren't stored. `GET /v1/audit` streams those columns as CSV. The file mode is forced to `0600`.

The phone database is Room, file name `fam_ai_audit.db`, schema version 3, also WAL, also insert-only by the same style of triggers. `AuditStore.kt` keeps the Pi columns and adds a thumbnail path, display name, crop, kind, confidence, lesion ratio, the advice JSON, a note flag, and the leaf-model fields (`leaf_label`, `leaf_confidence`, `leaf_next`, `leaf_advice_json`). Migrations 1 to 2 and 2 to 3 add those leaf columns. The history screen reads rows whose action is `diagnose`, `local_diagnose`, or `demo_diagnose`. The color heuristic that used to write `demo_diagnose` is still in `DemoClassifier.kt`, for unit tests. The live repository doesn't call it.

Settings that aren't secret (mode, address, language, region) sit in ordinary preferences. The default mode is `local`. The token and the farmer profile sit in `EncryptedSharedPreferences`, with an AES-256-GCM master key.

### Phone stack

From `android/gradle/libs.versions.toml` and `android/app/build.gradle.kts`:

- Kotlin 2.4.0, Android Gradle Plugin 8.13.2, Java 17, compile and target SDK 35, min SDK 26, version name 1.0.
- Jetpack Compose (BOM 2024.12.01) and Material 3, Navigation 2.8.5, lifecycle 2.8.7.
- CameraX 1.4.1 for capture. The camera is optional in the manifest, so a gallery photo still works.
- Room 2.8.5 with KSP 2.3.13.
- OkHttp 4.12.0 for the Pi, the Gemma download, and the optional share upload.
- Coil 2.7.0 for thumbnails. ExifInterface 1.3.7, so a sideways photo is rotated before we score it.
- `androidx.security:security-crypto` 1.1.0-alpha06 for the token and the farmer profile.
- LiteRT 2.3.2 (`org.tensorflow.lite.Interpreter`) for the leaf model. LiteRT-LM 0.19.1 for Gemma 4.

TFLite assets are stored uncompressed (`noCompress += "tflite"`) so the interpreter can map the file. Cleartext HTTP is allowed in `network_security_config.xml`, because the Pi has no certificate and Android ignores domain rules for raw IP addresses. `PiAddress` still rejects any host that isn't loopback, `.local`, or a private range, before a socket opens.

### Pi stack

`pi/requirements.txt` lists FastAPI 0.115 or newer, uvicorn 0.32 or newer, python-multipart, numpy 1.26 or newer, and Pillow 10 or newer. The ExecuTorch runtime is installed separately into the same virtualenv for aarch64. It isn't a pip line in that file. HailoRT is optional, and it isn't imported unless the Hailo backend is selected. OpenAPI, Swagger, and ReDoc are turned off.

### Training stack

`train/requirements.txt` lists torch, torchvision, Pillow, and pyarrow. The training script loads `EfficientNet_V2_S_Weights.IMAGENET1K_V1` and replaces the final linear layer. The comment at the top of `train/train_efficientnet.py` says the machine we used is a 4060 Ti with 16 GB.

### Layout we actually use

`android/app/src/main/java/ai/fam/app/ui/` holds the screens. `.../data/` holds the repository, the Pi client, the leaf model, Gemma, Room, and the share queue. `pi/farm_ai/api/` is the HTTP service and the audit log. `pi/farm_ai/vision/` selects mock, CPU, or Hailo and computes severity. `pi/farm_ai/llm/` builds the Llama prompt and falls back to the template. `pi/farm_ai/kb/retrieve.py` loads the notes. `kb/packs/` has one JSON file per downloadable language. `docs/reports/` is the shorter requirements, design, manual, and verification set. `docs/pi-cpu-install.md` and `pi/EXPORT.md` are the install and export notes. Weights, the token, both databases, the Android SDK path, and `train/data/` are gitignored.

## 5. Implementation & Core Modules

### 5.1 How a photo becomes a result

`FamNav.kt` starts on a welcome screen until a farmer profile is saved, then on the capture screen. The other routes are history, settings, `result/{id}`, and `advice/{id}`. Welcome collects a name and a 10-digit mobile number starting with 6, 7, 8, or 9. The farmer can continue as a guest, or register. Registration posts to `https://createmybills.tech` (`/api/fam/register/start` and `/api/fam/register/check`) when a share key was baked into the build from `local.properties`. Sharing photos for later training is a checkbox. The consent version in the code is `2026-11-16`.

Capture uses CameraX, or a gallery URI. `ImagePrep` copies the bytes, applies EXIF rotation, scales the long side to 768, and writes JPEG at quality 80. If the file is still over 2,000,000 bytes, quality drops by 10, down to 40. A 256-pixel thumbnail is kept for history. A sha256 is taken while the JPEG is written. A coarse color sample estimates lesion area by walking every other pixel and splitting plant pixels into healthy green versus yellow, orange, brown, and dark. That ratio is what severity uses. It's a color heuristic, not a segmentation model.

`DiagnoseRepository.run` then calls `LeafModel.predict`. The interpreter runs on four threads, with XNNPACK off. The bitmap is scaled so the short side is 416 and center-cropped to 384, matching training. Pixels are ImageNet-normalized with mean (0.485, 0.456, 0.406) and standard deviation (0.229, 0.224, 0.225). `LeafScores` turns logits into a softmax and keeps the top three. The input may be NCHW or NHWC; the code checks the tensor shape.

If mode is `pi`, the token isn't empty, and the address parses, `PiClient` spends up to two seconds on `/v1/health`. A failed health check is stored as `health_failure`, and the phone continues locally. A successful diagnose posts multipart fields: image, language, region id, device id, and an optional crop, with the bearer token. The Pi allows JPEG or PNG only, magic-byte checked, with a 2 MB cap. The phone's prepared JPEG fits inside that cap.

The local branch prefers Gemma. `PhoneVision` loads the LiteRT-LM engine, tries the GPU backend, then the CPU backend, and sends the photo as PNG plus a prompt built from the 30 knowledge-base labels and the short notes for the selected language. Sampling is top-k 20, top-p 0.8, temperature 0.2, seed 1. Thinking is disabled. If the model file is missing, or the JSON can't be parsed, and the leaf model did return a class, the repository writes a `local_diagnose` row with backend `on-device efficientnetv2`, model id `efficientnetv2_s`, and model version `plant-38`. If both fail, the farmer sees a missing-model or a failed-model outcome.

Advice for the leaf class is chosen in `careFor`. When the predicted id is one of the 30 knowledge-base labels, the text comes from `treatments.json`, the region note, and any saved language pack. When the id is outside that list, `LeafCare` supplies a shorter note. Healthy ids share one healthy note. Powdery, bacterial, citrus greening, grape esca, grape black rot, grape leaf blight, and strawberry leaf scorch have their own notes. Everything else gets a general "ask extension before you spray" note. Those LeafCare strings exist in English, Hindi, and Swahili. Any other language gets the English LeafCare text.

Gemma's own reply is used when it parses. If the summary is blank and the label is known, the stored note fills the gap, and the screen can say that the stored note is in English.

### 5.2 The Pi service

`python -m farm_ai.api` builds the app from environment variables in `pi/farm_ai/config.py`. The routes are:

- `GET /v1/health` returns `vision_backend` and `llm_backend`. No token. With the `.pte` files missing, both sides report `mock`. That checks the wiring. It isn't a model run.
- `POST /v1/diagnose` needs the bearer token, a UUID device id, a language code from `kb/languages.json`, and a known region. About 10 calls a minute are allowed per device. The eleventh returns HTTP 429. A public client returns HTTP 403 before the handler runs.
- `GET /v1/language-pack/{code}` returns one pack file. English, Hindi, and Swahili aren't served, because they already ship in the app.
- `GET /v1/audit` streams the CSV.

`pi/farm_ai/vision/factory.py` picks the backend once, at startup. Hailo is used only when it's requested and importable. Otherwise a present `efficientnetv2_s_int8_384.pte` selects the CPU runner. A missing file selects `MockVision`, which hashes the pixels and picks a label. That mock is deterministic, and it's what the tests exercise. The CPU runner in `cpu_executorch.py` requires the logit count to equal the number of entries in `kb/labels.json`. A 103-way checkpoint can't be dropped in as that `.pte` until it's exported with one logit per advice label, in that file's order. `pi/EXPORT.md` states the same rule. The service hashes the `.pte` once and stores the first 16 hex digits as the model version.

Lesion area on the Pi is measured again, on a 512-pixel copy, inside `PreparedImage.from_bytes`. Healthy ids are forced to low severity. The crop string is accepted and stored in the request checks, but the CPU runner ignores it (`del crop`). The mock runner does use it, to narrow the label pool. So a crop choice changes the mock, and it changes the Gemma prompt. It doesn't change the ExecuTorch argmax.

`advise` in `pi/farm_ai/llm/factory.py` asks Llama and parses one JSON object. Unusable JSON falls back to the translated note. The Hailo path never loads Llama. It returns the template directly, with `advice_source` of `template`. The response field `advice_language` is the language of the stored note when the text came from the template. The phone stores an `english_note` flag when that language is English and the farmer asked for something else.

### 5.3 Knowledge base, languages, and regions

`kb/labels.json` is a list of 30 objects. Each has an `id`, a `crop`, a `kind` (`disease` or `pest`), and a `display_name`. The crops with disease notes are apple, cassava, maize, potato, and tomato, and each of those has a healthy class. The pests are aphids, fall armyworm, whitefly, stem borer, and tomato spider mites. `retrieve.py` refuses to start if a required crop or pest is missing, if a disease crop has no healthy class, or if `treatments.json` has a different key set. The prompt's placeholders have to be exactly `language`, `language_name`, `region`, `knowledge`, and `diagnosis`.

`kb/languages.json` lists 24 languages. English, Hindi, and Swahili are `builtin: true`. The other 21 are Assamese, Bengali, Bodo, Dogri, Gujarati, Kannada, Kashmiri, Konkani, Maithili, Malayalam, Manipuri, Marathi, Nepali, Odia, Punjabi, Sanskrit, Santali, Sindhi, Tamil, Telugu, and Urdu. Each pack is `kb/packs/<code>.json`, version 1. The loader rejects a pack whose code doesn't match the file name, whose lists are a different length from the English lists, or whose summaries are still the English summaries. The phone repeats that check in `LanguagePack.parse` before it keeps the download. Packs aren't copied into the APK. Until a pack is saved, a template answer stays in English and the screen says so.

Regions are `indo_gangetic`, `peninsular_india`, and `east_african_highlands`, described in `kb/regions.json`. There is no weather client.

Listen uses the speech tags in the language list. Santali is stored in Ol Chiki. Manipuri uses the Bengali script so a typical phone font can draw it. A missing voice leaves the written advice in place.

### 5.4 The three class lists

This is the split we had to keep straight in the code.

`kb/labels.json` is the advice list. Thirty ids. The Pi classifier, the Gemma prompt, and `treatments.json` all use this list. Full notes, in three built-in languages plus packs, exist only here.

`train/data/classes.json` is the training list. One hundred and three ids, sorted alphabetically. `prepare_manifest.py` maps overlapping folder names onto the advice ids, so a tomato early-blight photo from PlantVillage and one from PlantDoc share `tomato_early_blight`. Crops that aren't in the advice list keep their own ids: rice, banana, groundnut, chilli, cashew, soybean diseases, cauliflower, radish, and others. Two advice ids aren't in the training list: `pest_aphids` and `pest_stem_borer`. We have notes for them. We didn't have a clean image class for them in the sets we kept. Twenty-eight of the thirty advice ids do appear in the 103.

`android/app/src/main/assets/leaf/classes.txt` is the phone list. Thirty-eight ids. It matches the earlier PlantVillage-aligned head: apple, blueberry, cherry, citrus greening, grape, maize, peach, pepper, potato, raspberry, soybean healthy, squash, strawberry, and tomato. It has no cassava classes and no pest classes. Twenty-one of those thirty-eight also sit in `kb/labels.json`, so they get the full treatment note. The other seventeen use `LeafCare`.

The checkpoint `efficientnetv2_s_plant.pt` stores its own `classes` list, the image size 384, the ImageNet mean and standard deviation, the architecture name, and the validation accuracy. `train/export_tflite.py` would export whatever that checkpoint contains today, which is the 103-class model, and it writes `classes.txt` and `leaf_meta.json` beside the TFLite file. The assets currently in the tree are still the 38-class export. We didn't replace them after the wider run.

### 5.5 Training

`train/download_datasets.py` fetches the unaugmented PlantVillage zip from Mendeley (so augmented copies aren't in both splits), the Indian soybean zip, the multicrop zip, and the Hugging Face mirrors we used when a zip was a poor fit. PlantDoc is `geraldmc/plantdoc-full`. `train/download_extra.py` fetches rice-india, Paddy Doctor, the CCMT tree, the cassava tree, and, as files on disk only, an insect-pest parquet set. `prepare_manifest.py` then skips that insect set. Its labels are bare integers, and the train, validation, and test files don't share one class list.

Class alignment is a table in that script, not a guess at export time. PlantVillage folder names and the TensorFlow Datasets index order both map through `PV`. PlantDoc string labels map through `PLANDOC`. Cassava CSV codes 0 to 4 map to bacterial blight, brown streak, green mottle, mosaic, and healthy. CCMT cassava "brown spot" is stored as `cassava_brown_spot`, and "green mite" as `cassava_green_mite`, because those aren't brown streak and green mottle. Tomato "leaf curl" in CCMT is mapped to `tomato_yellow_leaf_curl`. Whitefly filenames map to `pest_whitefly`. Official `train` and `test` folders are kept. Rows with no split are held out per class, 15 percent to validation, with seed 42, and a class of one image stays in training.

`summary.json` counts the sources after the four-file drop: PlantVillage 54,303, soybean 1,176, multicrop 21,874, PlantDoc 2,578, cassava 21,329, CCMT 2,197, rice-india 5,932, Paddy Doctor 10,407. That sums to 119,796 images and 103 classes. Support is uneven. `cassava_mosaic` has 13,210 images. `tomato_yellow_leaf_curl` has 5,532. `soybean_root` has 10.

`train/train_efficientnet.py` reads `manifest.csv` and `classes.json`. Training augmentation is resize to 416, a random resized crop to 384 (scale 0.7 to 1.0), a horizontal flip, and a small color jitter. Validation is resize to 416 and a center crop to 384. The sampler draws with weight `1 / count(label)`, so a rare class is seen about as often as a common one. Loss is cross-entropy with label smoothing 0.1. The optimizer is AdamW at learning rate 3e-4 and weight decay 1e-4, with a cosine schedule over 8 epochs. Mixed precision is float16 autocast and a gradient scaler. The script starts at batch 32. On `OutOfMemoryError` it empties the cache and halves the batch, down to 8. This run stayed at batch 32, which is the first line of `train/runs/train.log`.

A corrupt file doesn't kill the epoch. `__getitem__` tries the next row, up to six times, on `OSError` or `UnidentifiedImageError`. The checkpoint is overwritten whenever validation accuracy is at least as high as the best so far.

The log, rounded to three decimals:

| Epoch | Train accuracy | Validation accuracy | Validation loss |
| --- | --- | --- | --- |
| 1 | 0.889 | 0.865 | 1.170 |
| 2 | 0.950 | 0.922 | 1.008 |
| 3 | 0.961 | 0.927 | 0.982 |
| 4 | 0.970 | 0.941 | 0.954 |
| 5 | 0.979 | 0.943 | 0.940 |
| 6 | 0.984 | 0.958 | 0.908 |
| 7 | 0.988 | 0.955 | 0.910 |
| 8 | 0.990 | 0.953 | 0.910 |

Best validation accuracy 0.958. The saved checkpoint is the epoch-6 weights. Exact figures are in `train/runs/history.json`. Epoch 6 validation accuracy there is 0.9577785149030943.

The earlier log `train/runs/train.plantvillage.log` reaches a best validation accuracy of 0.990 at epochs 7 and 8 (the history file's epoch-8 value is 0.9896206156048676, which the print rounded to 0.990). `plantdoc_test.json` records 38 classes for that checkpoint and a PlantDoc test accuracy of 0.682 (161 of 236). We kept that checkpoint under the `.plantvillage.pt` name when the wider run replaced `efficientnetv2_s_plant.pt`.

`export_tflite.py` converts with `fold_fp16_resource_casts=False`. Float16 weights made the phone return one class for every photo, so the phone file is float32. The script runs one random tensor through PyTorch and through the TFLite interpreter, and it exits if the top class differs. `leaf_meta.json` records input shape `[1, 3, 384, 384]` and check class `tomato_septoria_leaf_spot` for the 38-class file that's in the app now.

### 5.6 Optional sharing

If the farmer agreed, `ShareSync` copies the JPEG and a JSON sidecar into `files/share-queue` and uploads them to `POST /api/fam/samples` with the header `X-Fam-Key`. The sidecar carries the name, mobile, role, consent, the leaf label, and the reply already shown. It doesn't pretend the label is a confirmed disease. Withdraw clears the local queue and sends `DELETE /api/fam/samples?mobile=...`. The queue isn't an audit log. The audit log still doesn't store image bytes.

## 6. Testing, Challenges & Solutions

### What we test

Pi tests live in `pi/tests/`: `test_api.py`, `test_parser.py`, `test_severity.py`, and `test_backend.py`. They post a PNG, check `advice_language`, refuse a public client, reject a bad token, require mode `0600` on the token file, hit the rate limit, and check that the audit CSV is append-only. Severity boundaries and the five advice keys are asserted. A Tamil pack fetch has to carry the token, and English isn't served as a pack. The verification note in `docs/reports/04-verification.md` maps each requirement id to one of these tests, or marks a gap.

Phone unit tests are `VisionReplyTest`, `LeafScoresTest`, `LeafCareTest`, `ShareQueueTest`, and `DemoLogicTest`. They cover Gemma JSON parsing, the Wi-Fi gate on the large download, softmax ranking, LeafCare language choice, the share queue rules, and the unused color heuristic. They don't load the Gemma file or the TFLite file, and they don't drive the camera. There's no automated UI test for the crop picker, the history screen, or the Listen button. We haven't run a hotspot test from the suite. Those gaps are written down in the verification file, rather than treated as covered.

### Slow Mendeley downloads

PlantVillage, soybean, and the multicrop set come from Mendeley. A single connection to that cache stayed near 50 KB/s, which would have left the soybean and multicrop zips running for about a day. `train/fetch_fast.py` resolves the public zip URL, then pulls 8 MB ranges on several connections. The script's own comment says the parallel ranges are still slow, and that they finish in hours instead of a day. If a range throws, the script prints `refresh url after` plus the error, resolves the public URL again, waits two seconds, and continues. Signed Mendeley links go stale. That refresh is how a refused range, including an HTTP 403 from an expired cache URL, gets a new address without starting the file over. Hugging Face parquet files were the easier path for PlantDoc, rice, and Paddy Doctor, and `download_datasets.py` says we didn't fetch Kaggle, IEEE Dataport, or Zenodo. The first two need an account, and the Zenodo API refused this machine.

### Cassava HTTP 429

The cassava tree is listed as 21,402 files in `train/data/extra/progress.log`. The download ended with `cassava errors 68`. `train/data/extra/cassava/errors.txt` has 68 lines: 63 are `HTTP Error 429: Too Many Requests`, four are read timeouts, and one is a DNS failure (`Name or service not known`). The progress line stopped at 21,334 of 21,402. The manifest kept 21,329 cassava images (`summary.json`). We trained on the images that arrived. We didn't invent labels for the missing files. `download_extra.py` writes at most the first 200 errors into `errors.txt`. All 68 fit.

### Corrupt and unlabeled images

`train/data/filter.log` records a pass over the manifest: it checked 62,919 of 119,800 files in the logged portion, then kept 119,796 and dropped 4. The four were one multicrop photo whose only label text was the bare word "rust", and three CCMT JPEGs that couldn't be read. A bare "rust" doesn't say which crop, and `FIELD_ALIAS` only remaps whitefly names, so we dropped it instead of inventing `maize_common_rust` or `cashew_red_rust`. During training, a file that still fails to open is skipped by trying the next row, up to six times, and only then raised.

### Class alignment

Putting every source in one softmax only works if the same disease has the same string in every set. We wrote the maps by hand in `prepare_manifest.py`, and left a comment where two cassava names look alike and aren't the same disease. The insect parquet set was downloaded far enough to see its schema, then left out of the manifest, because integer labels with a different class list per split would have trained the model to treat "class 3" as one disease when it isn't.

The mismatch we still have to close is inside our own tree. The advice head is 30-way, in `kb/labels.json` order. The new checkpoint is 103-way, in alphabetical order. The phone file is 38-way, from the earlier run. `cpu_executorch.py` will throw if a `.pte` returns a different count than the advice list. `LeafScores` returns nothing if the logit vector and `classes.txt` differ in length. So the 0.958 model is real, and it isn't yet the model the Pi service or the current APK will load. The phone still answers with the 38-class float32 file, and the Pi still expects a 30-logit program that we export separately.

### GPU batch fallback

The 4060 Ti has 16 GB. The script asks for batch 32 with mixed precision, which is what this run used from epoch 1. If CUDA raises out-of-memory, the batch is halved and the whole fit starts again, down to batch 8. Below that the process exits with `batch 8 still does not fit`. The fallback didn't fire on 17 November. It's there because a 384-pixel EfficientNet, plus the weighted sampler's memory traffic, doesn't leave much spare on a 16 GB card once the batch or the loader workers move.

### Other trade-offs we accepted

Float16 TFLite was smaller, and it was wrong. Every photo collapsed to one class. The export stays float32, about 81 MB, and the APK carries it.

Gemma 4 is the large download. `START-HERE.md` tells the farmer to expect about 3.7 GB, on Wi-Fi, for the model that answers when the Pi is away. In the code that file is `gemma-4-E4B-it.litertlm`. Mobile data is used only after the farmer allows it. `chooseDownload` waits when the phone is offline, refuses a metered network until the farmer allows it, and pauses in the background when data saver is on or background transfer is off. The download uses a `.partial` file and a range request so it can resume. While that file is absent, the bundled 38-class model is what answers, which is a quieter fallback than a blank screen.

Llama 3.2 1B on the Pi is asked to write all 24 languages. We don't expect the 1B model to carry the smaller languages reliably. The packs exist so the template path is already translated, and the phone Gemma path is the one we expect to rewrite a note when the pack is missing. A pack that is still English gets rejected, so we don't show English and call it Tamil.

The audit log can't be edited from SQL without the trigger aborting. That protects the trail. It also means a bad row stays. We store a hash and a thumbnail path on the phone, not the full image, so a lost thumbnail leaves the row and an empty picture.

A validation accuracy of 0.958 on a mixed manifest isn't the same claim as the 0.990 on the narrower run, and neither number is the PlantDoc field split. The only PlantDoc test figure we recorded is 0.682, on the 38-class checkpoint. We treat the new model as a lab result until we export it and score the field splits again.

## 7. Conclusion & Future Scope

Fam-AI runs a leaf check from a phone, with or without a Raspberry Pi on the village hotspot. The Pi path classifies with an ExecuTorch EfficientNet and writes advice from stored notes, with Llama 3.2 1B as a rewrite step that's allowed to fail. The phone path classifies with a bundled 38-class EfficientNet and, when the farmer has downloaded it, with Gemma 4. History, severity, and the five-part advice stay on the device. The audit trail stores a hash, not the photo. Twenty-four languages are listed. Three ship with the app. Twenty-one packs download from the Pi, and they're rejected unless they match the English note shape and are actually translated.

The training run we finished on 17 November 2026 is the widest model in the workspace: EfficientNetV2-S, 384 input, ImageNet normalization, 103 classes, 98,693 training images and 21,103 validation images, best validation accuracy 0.958 at epoch 6. The manifest behind it has 119,796 images from PlantVillage, soybean, multicrop, PlantDoc, cassava, CCMT, rice-india, and Paddy Doctor. The earlier 38-class checkpoint, best validation accuracy 0.990, is still the one exported into the app.

The next engineering step is an export, not another architecture. The 103-class checkpoint needs a float32 TFLite file whose `classes.txt` matches the checkpoint's class list, and a Pi program whose logits match `kb/labels.json`, either by training a 30-way head on the shared ids or by mapping the 103-way argmax onto those ids when the top class is one of them. Until that export exists, the accuracy in `train.log` and the label on the phone screen are two different models. Aphids and stem borer need real images before they can leave the notes and enter the classifier. The 68 missing cassava files can be fetched again once the host stops returning 429. Money plant, pothos, and jade need a dataset of their own if we want the app to name them. A fresh PlantDoc-style score on the 103-class checkpoint would tell us whether the 0.958 validation accuracy survives field photos. The unit tests we have cover the parsers, the severity cuts, and the token rules. A device pass on the camera, the crop picker, and a real hotspot is still open.
