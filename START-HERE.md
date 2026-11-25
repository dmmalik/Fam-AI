# Fam-AI — start here

This page is for a farmer, a family member, or anyone who isn't a programmer.

Fam-AI looks at a photo of a leaf and tells you what it might be: a disease, a pest, or a healthy leaf. It also says what to do today, what to do so the problem doesn't spread, and when to ask a local agriculture officer for help.

It does **not** name a medicine dose. It does **not** replace a visit to the agriculture office or a KVK. It's a photo check, not a lab test.

## Install the app on the phone

1. Copy `share/Fam-AI.apk` onto the Android phone. A cable, Bluetooth, or a pen drive will do.
2. Open the file on the phone.
3. If the phone asks, allow install from this source. Then tap Install.
4. Open the app. On the phone the name is Fam-AI.

The phone should be Android 8 or newer.

## Check a leaf

1. Open Fam-AI.
2. Tap **Take photo**, or pick a photo you already have.
3. If you know the crop, choose tomato, potato, cassava, maize, or apple. If you aren't sure, leave it as any crop.
4. Wait for the result.
5. Read the name, and whether it says low, medium, or high.
6. Open **Advice** for what to do now, treatment, prevention, and when to ask for help.
7. Tap **Listen** if you want the phone to read it aloud. If the phone has no voice for that language, the writing on the screen is still the whole message.

Old checks stay under **History**.

## Choose your language

Open **Settings** and pick your language. The name is written in that language.

English, Hindi, and Swahili are already in the app.

For another Indian language you'll see **Download language pack**. That pack is the crop notes in the language you picked. It comes from the small field computer (the Pi), not from the open internet. Save the Pi address and the token first, then tap download. Until the pack is saved, the written note stays in English, and the screen says so.

## The small field computer (optional)

Some villages keep a Raspberry Pi on a local Wi-Fi that doesn't need the public internet. If you have one:

1. Connect the phone to that Wi-Fi.
2. In Settings, choose **Pi**.
3. Enter the address. On the usual hotspot it's `192.168.4.1`, and the port is `8080`.
4. Paste the token the person who set up the Pi gives you. Save.

If the Pi is off, the phone can still try to answer on its own once a large plant model has been downloaded. That download is about 3.7 GB, and it uses Wi-Fi.

## What the result means

- **Low / medium / high** is how serious the photo looks. It isn't a promise.
- The app covers tomato, potato, cassava, maize, and apple, plus a few common pests such as aphids and fall armyworm.
- If the problem is spreading, take the leaf, or this result, to the agriculture officer.

## हिंदी में, छोटे में

Fam-AI पत्ते की फ़ोटो देखकर बीमारी या कीड़े का नाम बताता है, और आज क्या करना है यह लिखता है। यह दवा की मात्रा नहीं बताता। यह कृषि अधिकारी की जगह नहीं लेता।

ऐप लगाने के लिए फ़ोन में `share/Fam-AI.apk` खोलें और Install दबाएँ। फ़ोटो लें, नतीजा पढ़ें, और सलाह सुनने के लिए Listen दबाएँ। अंग्रेज़ी और हिन्दी ऐप में पहले से हैं।

## For a technician

Setup notes for the Pi, the requirements, and the design are in [docs/reports/05-project-report.md](docs/reports/05-project-report.md).
