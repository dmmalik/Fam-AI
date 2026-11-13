# Fam-AI user manual

A shorter page, written for someone who only wants to install the app, is [START-HERE.md](../../START-HERE.md). The app file is [share/Fam-AI.apk](../../share/Fam-AI.apk).

## Farmer

1. Open Fam-AI. The first screen is the photo.
2. Choose a crop, or leave it as any crop.
3. Take a photo of the leaf, or choose one from the gallery.
4. Read the name, the severity, and the advice. Open Advice if you want the steps on their own screen.
5. Tap Listen to hear the advice. If this phone has no voice for the language, the writing is the full message. Add a voice from the phone's own voice installer. A language pack does not contain speech.
6. History keeps the checks on this phone.

### Language

Open Settings and choose a language. The name is written in that language.

English, Hindi, and Swahili are already on the phone. You do not download them.

For any other language, a Language pack row appears. The pack is the crop notes for that language. It comes from the field box, not from the internet at large.

1. Save the field-box address and the token first.
2. Tap Download language pack. The row shows the size and the progress.
3. The download stays off mobile data unless you already allowed mobile data for the plant model. On the field hotspot it does not need public internet.
4. When the row says the pack is saved, later checks can use those notes.
5. Remove language pack deletes it. Advice for that language goes back to the English note.

If the token is missing or the field box does not answer, the row says the pack needs the field box.

If a check still used the English note, the result says: the stored note is in English.

Santali is written in Ol Chiki. If the phone has no font for those letters, switch to Hindi or English. Manipuri is written in the Bengali script.

### Regions

Settings offers three regions: the Indo-Gangetic plains, peninsular India, and the East African highlands. The notes for the region follow the language when a translation is stored or a pack is saved.

### Phone model

Local mode uses a plant model of about 3.7 GB. It downloads on Wi-Fi while Fam-AI is open unless you allow background download or mobile data. Until it finishes, the phone cannot answer a photo by itself. Pi mode can still answer when the field box is up.

## Operator

Use 64-bit Raspberry Pi OS on a Pi 5. The CPU install, with no AI HAT, is [docs/pi-cpu-install.md](../pi-cpu-install.md). Model export is [pi/EXPORT.md](../../pi/EXPORT.md).

Create the token outside the repo, mode `0600`, as the install note shows. Bind the Pi's own address. On the hotspot that address is `192.168.4.1`. Do not bind `0.0.0.0`.

If crop-note files are in `kb/packs/`, the service reads them at startup. If that folder is empty, the service still starts and the phone keeps the English note until a pack is added. `GET /v1/health` from a phone on that network shows the backends. `GET /v1/language-pack/ta` with the bearer token returns the Tamil pack when that file is present. English, Hindi, and Swahili return a not-found response because the phone already has them.

In the app, switch mode to Pi, set the host and port `8080`, paste the token, and save.
