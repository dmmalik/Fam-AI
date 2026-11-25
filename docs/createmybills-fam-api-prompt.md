# Cursor prompt: Fam-AI leaf samples on createmybills.tech

Paste this whole file into Cursor in the createmybills.tech project. Follow the language, framework, database, and admin login that project already uses. Don't invent a second stack. Don't commit secrets.

Fam-AI is an Android app. After a farmer agrees, the phone may upload a leaf photo and the reply already shown on the phone. The label is the phone's guess, not a confirmed disease. Keep the files outside the public web root.

## Consent

Store the consent version, the consent time, the role, and whether the mobile was checked. Reject `POST /api/fam/samples` when `share_purpose` isn't `model_training`. A guest doesn't send an SMS session token. A registered farmer does.

If this project already sends SMS, use that sender for the registration code. If it doesn't, add one function that sends the code and, in development only, log the code. Don't print the code in production.

## Endpoints

All of these need the header `X-Fam-Key`, matching a server secret that isn't in git.

`POST /api/fam/register/start`

JSON body:

```json
{"name": "Ramu", "mobile": "9876543210"}
```

`mobile` is 10 digits, starting with 6, 7, 8, or 9. Send a short numeric code by SMS. Respond `200` with `{"ok": true}`, or `400` with `{"error": "..."}`.

`POST /api/fam/register/check`

JSON body:

```json
{"mobile": "9876543210", "code": "123456"}
```

Respond `200` with `{"session_token": "..."}` when the code matches and is still fresh. Otherwise `400` with `{"error": "..."}`.

`POST /api/fam/samples`

Multipart form:

- `photo`: the JPEG file
- `meta`: one JSON object, as a text field, with these names:

```json
{
  "farmer_name": "Ramu",
  "mobile": "9876543210",
  "role": "guest",
  "mobile_verified": false,
  "share_purpose": "model_training",
  "consent_version": "2026-11-16",
  "consent_at": "2026-11-16T12:00:00Z",
  "captured_at": "2026-11-16T12:05:00Z",
  "leaf_label": "Tomato early blight",
  "leaf_confidence": 0.87,
  "leaf_next": "Tomato late blight 4%",
  "care": {"summary": "", "immediate_actions": [], "treatment": [], "prevention": [], "when_to_seek_help": ""},
  "written_reply": {"summary": "", "immediate_actions": [], "treatment": [], "prevention": [], "when_to_seek_help": ""},
  "language": "hi",
  "region": "indo_gangetic",
  "model_id": "efficientnetv2_s",
  "model_version": "plant-38",
  "backend": "on-device efficientnetv2"
}
```

`role` is `registered` or `guest`. When `role` is `registered`, also require `Authorization: Bearer <session_token>`. Limit the JPEG to 3 MB. Respond `201` with `{"id": "..."}`.

`DELETE /api/fam/samples?mobile=9876543210`

Delete every stored photo and row for that mobile. Registered callers also send the bearer token. Respond `200` with `{"deleted": 3}`.

## Review page

Behind the site's existing admin login, add a list of uploads and a detail page. The detail page shows the photo beside the leaf class, the confidence, the care steps (`summary`, `immediate_actions`, `treatment`, `prevention`, `when_to_seek_help`), and the written reply in the same shape. Show the farmer name, mobile, role, and whether the mobile was checked. Don't call the class a confirmed disease.

## Storage

Save the JPEG on disk, outside the public web root, and the metadata in the project's database. This store is for later training. Keep the phone's guess as the label. Don't mix it into a verified training set.
