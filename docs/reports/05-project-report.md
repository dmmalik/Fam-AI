# Fam-AI project report

If you aren't a programmer, read [START-HERE.md](../../START-HERE.md) first. This page is the technical cover.

Fam-AI is a field leaf check for a farmer. The phone takes the photo. A Raspberry Pi on a private hotspot classifies it and writes advice from stored notes. When that box is away, the phone can answer with Gemma 4, after the farmer downloads the model. The advice doesn't invent a spray dose. It says what to do now, what to ask an extension officer about, and when to get help.

## What was built

- An Android app with photo, history, settings, spoken advice, and a plant-model download.
- A Pi service with health, diagnose, audit, and language-pack routes.
- Notes for tomato, potato, cassava, maize, and apple, including the listed pests.
- Three regions: Indo-Gangetic plains, peninsular India, and the East African highlands.
- Twenty-four languages. English, Hindi, and Swahili notes are built in. The other scheduled languages of India are packs the phone downloads from the field box.

## How the pieces fit

A check on the hotspot never needs the public internet. The box refuses a public bind and a public client. The token stays out of the git tree. The audit log stores a hash of the photo, not the photo itself.

Once a language pack is saved, it's used the same way as the Hindi and Swahili notes. Before that, a template answer stays in English and the screen says so. Llama on the Pi is asked to write in the language the farmer picked. The smaller languages should come through more reliably on the phone model than on the 1B model.

## Where to read the rest

- [00-baseline.md](00-baseline.md) — inventory and glossary
- [01-srs.md](01-srs.md) — requirements and IDs
- [02-sdd.md](02-sdd.md) — design
- [03-user-manual.md](03-user-manual.md) — farmer and operator steps
- [04-verification.md](04-verification.md) — tests and gaps

Install steps for the Pi without an AI HAT stay in [docs/pi-cpu-install.md](../pi-cpu-install.md).

## Limits

The check is one label for one photo. It isn't a laboratory test. Weights aren't in the repository. A language other than English, Hindi, or Swahili uses a crop-note pack from `kb/packs` when that file is present. Otherwise the stored note stays in English. Santali text needs an Ol Chiki font. If the phone has no voice, the written advice stays on the screen.
