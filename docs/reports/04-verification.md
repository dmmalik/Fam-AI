# Verification

Each ID from [01-srs.md](01-srs.md) is traced to a test or marked as a gap. Tests live in `pi/tests/` and `android/app/src/test/`.

| ID | Evidence | Gap |
| --- | --- | --- |
| FR-CAP-1 | Phone capture screen. No automated UI test. | Gap: no device test for the camera button. |
| FR-CAP-2 | `DemoLogicTest` covers crop filtering in the unused color heuristic. The live path sends the crop string to the box. | Gap: no UI test of the crop picker. |
| FR-DIAG-1 | `test_api.py` posts a PNG and checks the diagnose body, including `advice_language`. | |
| FR-DIAG-2 | `VisionReplyTest` parses Gemma JSON and checks the Tamil prompt names Tamil. | Gap: the unit test does not load the Gemma file. |
| FR-DIAG-3 | Strings and `ModelDownload` gates are covered by `VisionReplyTest.downloadStaysOnWifiUntilTheUserAllowsMore`. | |
| FR-ADV-1 | `test_parser.py` checks the five advice keys. | |
| FR-ADV-2 | `kb/prompts/advisory.txt` is loaded by `KnowledgeBase`, which rejects a prompt whose placeholders changed. | Gap: no test that a model refused a dose, because the test model returns canned JSON. |
| FR-ADV-3 | `test_severity.py`. | |
| FR-LANG-1 | `kb/languages.json` and `LanguageCatalog.parse`. | Gap: no unit test loads the asset file. |
| FR-LANG-2 | `test_notes_cover_every_label_in_three_languages`. | |
| FR-LANG-3 | `test_unknown_language_is_rejected_and_tamil_uses_the_pack` fetches the Tamil pack with the token, rejects a missing token, and does not serve English. | Gap: no device test of the settings button. |
| FR-LANG-4 | Phone stores `english_note` when `advice_language` is `en` for another language. | Gap: no unit test of that flag. |
| FR-LANG-5 | `FarmerListen` uses the speech tags. | Gap: no test of the speech engine. |
| FR-REG-1 | Diagnose requires a known region. Hindi region text is asserted in `test_parser.py`. | |
| FR-HIST-1 | Room history query. | Gap: no UI test. |
| FR-SET-1 | `PiAddress` parsing is used before a socket opens. Token storage is encrypted preferences. | Gap: no unit test of the encrypted store. |
| FR-MOD-1 | `chooseDownload` cases in `VisionReplyTest`. | |
| NFR-SEC-1 | `test_public_client_is_refused`. | |
| NFR-SEC-2 | `test_bad_token_and_rejected_upload_are_audited` and `test_token_file_must_be_mode_0600`. The pack route uses the same token check. | |
| NFR-SEC-3 | `test_rate_limit`. | |
| NFR-SEC-4 | `test_audit_csv_header_and_append_only`. | |
| NFR-OFF-1 | Bind address and pack URL are the private field box. | Gap: no test runs on a hotspot. |
| NFR-SAFE-1 | Pack load fails closed when a pack is missing, the wrong length, or still English. The Tamil test checks the template summary matches the pack. | |
| CON-1 | `.gitignore` excludes weights, token files, databases, training photos, and build outputs. | |
| CON-2 | Label loader requires one class list. | |
| CON-3 | No weather client exists in the tree. | |
| CON-4 | Pack validation and the advisory prompt. | |
| CON-5 | `test_tamil_template_uses_the_pack_not_english` shows the pack path. Without a pack the loader does not start, so the English fallback is the phone behavior when the file was not downloaded. | |

Run the Pi tests from `pi/` with the project virtual environment:

```bash
python -m pytest
```

Run the phone unit tests from `android/`:

```bash
bash ./gradlew :app:testDebugUnitTest
```
