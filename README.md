# Fam-AI

The phone takes a leaf photo. A Raspberry Pi on the local hotspot runs the diagnosis. The phone can also answer with a downloaded Gemma model when the Pi is not there.

If you are installing the app and you are not a programmer, start with [START-HERE.md](START-HERE.md). The app file is [share/Fam-AI.apk](share/Fam-AI.apk). The technical report is in [docs/reports/05-project-report.md](docs/reports/05-project-report.md).

Vision is EfficientNetV2-S, int8, 384 input. Advice is Llama 3.2 1B Instruct, capped at 256 new tokens. Both run through ExecuTorch. Weight files stay out of git. Export steps are in [pi/EXPORT.md](pi/EXPORT.md). The CPU install, with no Hailo HAT, is in [docs/pi-cpu-install.md](docs/pi-cpu-install.md). What the two pipelines do is in [PROJECT.md](PROJECT.md).

## Pi service

64-bit Raspberry Pi OS on a Pi 5. From `pi/`:

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
```

Install the ExecuTorch runtime for aarch64 into that same venv. Copy the two exported programs into `pi/models/`:

- `efficientnetv2_s_int8_384.pte`
- `llama32_1b_instruct.pte`

The token file is outside the repo, mode `0600`. The process exits if that mode is wrong.

```bash
sudo install -d -m 0700 /var/lib/fam-ai
python - <<'PY'
import secrets, pathlib
path = pathlib.Path("/var/lib/fam-ai/token")
path.write_text(secrets.token_urlsafe(32) + "\n")
path.chmod(0o600)
PY
```

Bind the Pi's own address. The service refuses `0.0.0.0`. On the hotspot that address is `192.168.4.1`.

```bash
export FAM_AI_HOST=192.168.4.1
export FAM_AI_PORT=8080
export FAM_AI_TOKEN_FILE=/var/lib/fam-ai/token
export FAM_AI_DB=/var/lib/fam-ai/audit.db
unset FAM_AI_HAILO
python -m farm_ai.api
```

`GET /v1/health` from a phone on that network returns `vision_backend` and `llm_backend`. With the `.pte` files missing, health shows `mock`. That checks the wiring. It is not a model run.

The audit database is `/var/lib/fam-ai/audit.db`. `GET /v1/audit` needs the bearer token and streams the CSV. `GET /v1/language-pack/{code}` uses the same token. When a file for that language is in `kb/packs/`, the Pi returns it. English, Hindi, and Swahili are already on the phone, so those three codes are not served.

## Android app

Kotlin and Jetpack Compose. Min SDK 26, target 35. Demo mode is the default until a token and a reachable Pi are saved.

```bash
cd android
bash ./gradlew :app:assembleDebug
```

A copy for sharing is `share/Fam-AI.apk`. The build also writes `android/app/build/outputs/apk/debug/app-debug.apk`. In settings, switch the mode to Pi, set the host and port `8080`, and paste the token from `/var/lib/fam-ai/token`. The result screen names the backend that ran.

`android/local.properties` points at the SDK on this machine. It is not committed. Set `ANDROID_HOME` if the wrapper cannot find the SDK.
