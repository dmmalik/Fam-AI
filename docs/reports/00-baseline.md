# Fam-AI baseline

This file is the shared glossary and the inventory the other reports use. Requirement IDs are defined only in the SRS.

## What a farmer gets

The farmer takes or chooses a leaf photo on a phone. Fam-AI names the crop problem, gives a severity, and writes short advice: a summary, what to do now, treatment, prevention, and when to ask for help. The advice does not invent a product or a dose. It points the farmer to a registered product and to an extension officer, a KVK, or an agrovet.

The check can run on a Raspberry Pi 5 that the phone reaches over a private hotspot, or on the phone itself with Gemma 4 after that model has been downloaded. The result screen names which path ran. History stays on the phone. A Listen button speaks the advice when the phone has a voice for the selected language. The writing stays on the screen when it does not.

## Languages and regions

The farmer chooses one of 24 languages. The list is `kb/languages.json`.

Built in, with crop notes stored in `kb/treatments.json` and `kb/regions.json`:

- English `en`
- Hindi `hi`
- Swahili `sw`

The other languages can use a crop-note pack. When a file is present under `kb/packs/`, the Pi serves it and the phone can download it. Until that file is saved on the phone, the stored note stays in English. The pack languages are:

Assamese, Bengali, Bodo, Dogri, Gujarati, Kannada, Kashmiri, Konkani, Maithili, Malayalam, Manipuri, Marathi, Nepali, Odia, Punjabi, Sanskrit, Santali, Sindhi, Tamil, Telugu, and Urdu.

A pack translates the English notes. It does not add a product, a dose, or a new claim. English, Hindi, and Swahili have no download button. Until a pack is on the phone, a template answer for another language stays in English, and the screen says the stored note is in English.

The three regions stay: Indo-Gangetic plains, Peninsular India, and East African highlands. There is no weather feed.

## What is in the tree

- `android/` is the phone app. Kotlin and Jetpack Compose. Minimum SDK 26, target 35.
- `pi/farm_ai/` is the hotspot service.
- `kb/` holds labels, treatments, regions, the language list, prompts, and the packs.
- `train/` holds the scripts that prepare leaf photos and fine-tune EfficientNetV2-S. Downloaded photos, checkpoints, and partial archives are not part of the tree that is shared.
- `docs/pi-cpu-install.md` and `pi/EXPORT.md` stay the install and export notes.

Weight files, the bearer token, the audit database, and the Android SDK are not in the tree.

## Glossary

| Term | Meaning |
| --- | --- |
| Field box | The Raspberry Pi 5 on the private hotspot. |
| Built-in language | English, Hindi, or Swahili. Notes ship inside the app and the Pi knowledge base. |
| Language pack | The crop and region notes for one other language, downloaded from the field box. |
| Stored note | The text in the knowledge base or in a saved pack. It is the only source of agronomy claims. |
| Template | Advice taken from the stored note because the language model did not return usable JSON, or because the Hailo path does not run Llama. |
| Backend | The name shown for the path that ran: `cpu`, `hailo`, `mock`, or `on-device gemma4`. |

## Known limits

The vision model is one classifier, not a detector. It covers tomato, potato, cassava, maize, and apple, including aphids, fall armyworm, whitefly, stem borer, and tomato spider mites. Severity for leaf disease follows lesion area: under 10 percent is low, 10 to 30 percent is medium, and above 30 percent is high. Pests use confidence: under 0.60 is low, from 0.60 up to 0.85 is medium, and 0.85 and above is high.

Llama 3.2 1B on the Pi often fails to write the smaller languages. The phone Gemma path is the one expected to carry them. A missing voice or a missing font, especially Ol Chiki for Santali, leaves the written advice as the message. Manipuri packs and the Manipuri interface use the Bengali script so a typical phone font can draw them.
