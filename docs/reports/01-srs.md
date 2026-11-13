# Software requirements specification

Fam-AI, field leaf check. This specification matches the tree described in [00-baseline.md](00-baseline.md). Later reports cite these IDs and do not invent new ones.

Status values: implemented, partial, not in tree.

## 1. Purpose

Fam-AI lets a farmer check a leaf on a phone without sending the photo to the public internet. The field box on a private hotspot classifies the photo and writes advice from stored notes. The phone can answer with Gemma 4 when the box is not available.

The result is a field check. It is not a laboratory test and it does not replace an extension officer.

## 2. Users

- Farmer: takes the photo, reads or hears the advice, looks at history.
- Operator: installs the field box, creates the token, and tells the farmer the address.

## 3. Operating environment

- Phone: Android 8 or newer, camera optional, English plus the languages in `kb/languages.json`.
- Field box: 64-bit Raspberry Pi OS on a Pi 5, private address, port 8080 by default.
- Vision weights and the Llama program are installed on the box and are not in git. The phone model is downloaded by the farmer and is not in git.

## 4. Interfaces

- Phone screens: photo, history, settings, result, advice.
- `GET /v1/health` returns the vision and language-model backends. No token.
- `POST /v1/diagnose` accepts a JPEG or PNG up to 2 MB, plus language, region, device id, optional crop, and a bearer token.
- `GET /v1/language-pack/{code}` returns one pack. Bearer token. Private network only.
- `GET /v1/audit` streams the audit CSV. Bearer token.

The diagnose response includes `advice_language`. When the text is a template and no pack or built-in note exists for the requested language, that field is `en`.

## 5. Functional requirements

| ID | Requirement | Status |
| --- | --- | --- |
| FR-CAP-1 | The farmer can take a photo or choose one from the gallery. | implemented |
| FR-CAP-2 | The farmer can leave the crop unset or set tomato, potato, cassava, maize, or apple. | implemented |
| FR-DIAG-1 | In Pi mode, with a saved token and a reachable box, the phone sends the photo to `POST /v1/diagnose`. | implemented |
| FR-DIAG-2 | In local mode, or when the box does not answer, the phone asks Gemma 4 on the device. | implemented |
| FR-DIAG-3 | If the phone model is not installed yet, the farmer is told to wait for that download. | implemented |
| FR-ADV-1 | Advice has a summary, steps to do now, treatment, prevention, and when to ask for help. | implemented |
| FR-ADV-2 | The model is told not to add a product, a dose, or a claim the notes do not support. | implemented |
| FR-ADV-3 | Severity is low, medium, or high, using lesion area for disease and confidence for pests. | implemented |
| FR-LANG-1 | Settings lists the 24 languages, each by its own name. | implemented |
| FR-LANG-2 | English, Hindi, and Swahili notes are built in. Those three have no pack button. | implemented |
| FR-LANG-3 | For any other selected language, settings offers a pack download from the field box, with size, progress, Wi-Fi and mobile-data gates, retry, and remove. | implemented |
| FR-LANG-4 | When the stored note used for a template answer is English and the farmer chose another language, the result and advice screens say the stored note is in English. | implemented |
| FR-LANG-5 | Listen speaks the advice in the selected language when a voice is installed. Otherwise the writing remains. | implemented |
| FR-REG-1 | The farmer chooses Indo-Gangetic plains, Peninsular India, or East African highlands. Region notes come from the built-in file or from the saved pack. | implemented |
| FR-HIST-1 | Successful checks stay on the phone and can be opened again. | implemented |
| FR-SET-1 | Settings saves mode, field-box address, token, language, and region. The token is stored encrypted. | implemented |
| FR-MOD-1 | The plant model download states its size, stays on Wi-Fi unless the farmer allows mobile data, and can resume. | implemented |

## 6. Non-functional requirements

| ID | Requirement | Status |
| --- | --- | --- |
| NFR-SEC-1 | The service refuses to bind `0.0.0.0` and refuses clients that are not on a private range. | implemented |
| NFR-SEC-2 | Diagnose, audit, and language-pack calls compare the bearer token in constant time. The token file is mode `0600` outside the repo. | implemented |
| NFR-SEC-3 | About 10 diagnose calls a minute are allowed per device. | implemented |
| NFR-SEC-4 | The audit log is insert-only. It stores a hash of the image, not the image bytes. | implemented |
| NFR-OFF-1 | A diagnosis on the hotspot does not require the public internet. The pack download uses the field box, not a public site. | implemented |
| NFR-SAFE-1 | A pack is saved only when every label is present, list lengths match the English notes, and the text is not still the English summaries. | implemented |

## 7. Constraints

| ID | Constraint | Status |
| --- | --- | --- |
| CON-1 | Weight files, the token, and the audit database are not committed. | implemented |
| CON-2 | One classifier covers the label list. It is not a detector. | implemented |
| CON-3 | There is no weather API. | implemented |
| CON-4 | Packs translate the English notes only. They do not add a product or a dose. | implemented |
| CON-5 | Advice in a language without a built-in note and without a saved pack is partial: the model may rewrite the English note, and the template path stays in English. | partial |

## 8. Out of scope

Accounts, cloud storage of photos, a public pack server, spray calendars, and a replacement for an extension visit.
