# Fam-AI — start here

This page is for a farmer, a family member, or anyone who is not a programmer.

Fam-AI looks at a photo of a leaf and tells you what it might be: a disease, a pest, or a healthy leaf. It also tells you what to do today, what to do so it does not spread, and when to ask a local agriculture officer for help.

It does **not** name a medicine dose. It does **not** replace a visit to the agriculture office or a KVK. It is a photo check, not a lab test.

## Install the app on the phone

1. Copy the file `share/Fam-AI.apk` onto the Android phone. You can use a cable, Bluetooth, or a pen drive.
2. Open the file on the phone.
3. If the phone asks, allow install from this source. Then tap Install.
4. Open the app. The name on the phone is Fam-AI.

The phone should be Android 8 or newer.

## Check a leaf

1. Open Fam-AI.
2. Tap **Take photo**, or choose a photo you already have.
3. If you know the crop, pick tomato, potato, cassava, maize, or apple. If you are not sure, leave it as any crop.
4. Wait until the result appears.
5. Read the name and whether it is low, medium, or high.
6. Open **Advice** for what to do now, treatment, prevention, and when to ask for help.
7. Tap **Listen** if you want the phone to read it aloud. If the phone has no voice for that language, the writing on the screen is the full message.

Old checks stay under **History**.

## Choose your language

Open **Settings**. Pick your language. The name is written in that language.

English, Hindi, and Swahili are already inside the app.

For another Indian language, you will see **Download language pack**. That pack is the crop notes in that language. It comes from the small field computer (the Pi), not from the open internet. Save the Pi address and the token first, then tap download. Until the pack is saved, the written note stays in English, and the screen says so.

## The small field computer (optional)

Some villages keep a Raspberry Pi on a local Wi-Fi that does not need the public internet. If you have one:

1. Connect the phone to that Wi-Fi.
2. In Settings, choose **Pi**.
3. Enter the address. On the usual hotspot it is `192.168.4.1` and the port is `8080`.
4. Paste the token the person who set up the Pi gives you. Save.

If the Pi is off, the phone can still try to answer by itself after a large plant model has been downloaded. That download is about 3.7 GB and uses Wi-Fi.

## What the result means

- **Low / medium / high** is how serious the photo looks, not a promise.
- The app covers tomato, potato, cassava, maize, and apple, plus a few common pests such as aphids and fall armyworm.
- Take the leaf, or this result, to the agriculture officer if the problem is spreading.

## हिंदी में, छोटे में

Fam-AI पत्ते की फ़ोटो देखकर बीमारी या कीड़े का नाम बताता है, और आज क्या करना है यह लिखता है। यह दवा की मात्रा नहीं बताता। यह कृषि अधिकारी की जगह नहीं लेता।

ऐप लगाने के लिए फ़ोन में `share/Fam-AI.apk` खोलें और Install दबाएँ। फ़ोटो लें, नतीजा पढ़ें, और सलाह सुनने के लिए Listen दबाएँ। अंग्रेज़ी और हिन्दी ऐप में पहले से हैं।

## For a technician

The setup notes for the Pi, the requirements, and the design are in [docs/reports/05-project-report.md](docs/reports/05-project-report.md).
