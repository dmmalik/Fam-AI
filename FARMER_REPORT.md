# Fam-AI: A Leaf Check You Can Run on Your Phone

## 1. Title & What This Project Is

Fam-AI is a phone app for farmers. You take a photo of a leaf, and the app tells you what that photo might be showing: a disease, a pest, or a healthy leaf.

It also tells you:

- what to do today
- how to slow the spread
- when to ask a local agriculture officer for help

It's a photo check. It isn't a lab test. It won't name a medicine dose, and it doesn't take the place of a visit to the agriculture office or a KVK.

The leaf checker was trained on a lot of crop photos, so it can recognise the common leaf problems.

## 2. Who It Is For

Fam-AI is for:

- farmers who want a quick check out in the field
- family members who help on the farm
- teachers and field workers showing someone else a simple phone tool
- anyone who isn't a programmer and just wants clear, practical steps

You need an Android phone, Android 8 or newer. You don't need to know how computers work.

## 3. How It Helps Farmers

Leaf spots and pests are easy to misname at a glance. Spray the wrong thing and you waste money, and you can harm the insects that were helping you. Extension offices and KVKs still make the final call. They just aren't standing in every plot the morning the first spots show up.

Fam-AI is for that gap, before the visit:

- One photo of the leaf
- A short name for what it might be
- Low, medium, or high, so you can see how serious the photo looks
- Written advice you can read, or hear out loud
- A history of past checks, kept on your phone

**Crops covered:** tomato, potato, cassava, maize, and apple.

**Common pests covered:** aphids, fall armyworm, and a few others such as whitefly and stem borer.

Money plant and other houseplants aren't on the list. Don't expect a useful name for those.

## 4. How Easy It Is to Use

### Install the app

1. Copy `share/Fam-AI.apk` onto the Android phone. A cable, Bluetooth, or a pen drive will do.
2. Open the file on the phone.
3. If the phone asks, allow install from this source. Then tap Install.
4. Open the app. On the phone the name is Fam-AI.

### Check a leaf

1. Open Fam-AI.
2. Tap **Take photo**, or pick a photo you already have.
3. If you know the crop, choose tomato, potato, cassava, maize, or apple. If you aren't sure, leave it as any crop.
4. Wait for the result.
5. Read the name, and whether it says low, medium, or high.
6. Open **Advice** for what to do now, treatment ideas, prevention, and when to ask for help.
7. Tap **Listen** if you want the phone to read it aloud. If the phone has no voice for that language, the writing on the screen is still the whole message.

Old checks stay under **History**.

## 5. Languages You Can Use

Open **Settings** and pick your language. The name is written in that language.

**Already inside the app (nothing to download):**

- English
- Hindi
- Swahili

**Other Indian languages:** you'll see **Download language pack**. That pack is the crop notes in the language you picked. It comes from the small field computer (the Pi), not from the open internet. Save the Pi address and the token first, then tap download.

You can get packs for Assamese, Bengali, Bodo, Dogri, Gujarati, Kannada, Kashmiri, Konkani, Maithili, Malayalam, Manipuri, Marathi, Nepali, Odia, Punjabi, Sanskrit, Santali, Sindhi, Tamil, Telugu, and Urdu.

Until the pack is saved, the written note stays in English, and the screen says so. Listen still only works if your phone has a voice for that language.

## 6. What the Result Means

- **Low / medium / high** is how serious the photo looks. It isn't a promise, and it isn't a lab report.
- The name is a likely disease, pest, or healthy leaf, for the crops we cover.
- **Advice** has five parts: a short summary, what to do now, treatment ideas, prevention, and when to seek help.
- If the problem is spreading, take the leaf, or this result, to the agriculture officer.

**What the app doesn't do:**

- It doesn't tell you a medicine dose, or a product brand to buy.
- It doesn't replace an agriculture officer, a KVK, or an agrovet.
- It's a photo check, not a lab test.
- It doesn't cover every plant. Money plant and a lot of houseplants sit outside its list.

## 7. Optional Village Computer (Pi)

Some villages keep a small Raspberry Pi on a local Wi-Fi that doesn't need the public internet. You don't have to have one.

If you do:

1. Connect the phone to that Wi-Fi.
2. In Settings, choose **Pi**.
3. Enter the address the person who set it up gives you. On the usual hotspot that's `192.168.4.1`, port `8080`.
4. Paste the token they give you, and save.

Language packs come from this box too. If the Pi is off, the phone can still try to answer on its own after a large plant model has been downloaded. That file is about 3.7 GB, and it downloads on Wi-Fi.

## 8. Closing Note

Fam-AI is meant to sit in your pocket as a first look at a troubled leaf. Take the photo, read the result, listen if you want, and use the advice to decide the next step. When the problem is spreading, or you aren't sure, take the leaf to your local agriculture officer. The app helps you walk into that conversation prepared. It doesn't take their place.
