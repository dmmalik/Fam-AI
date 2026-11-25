# Raspberry Pi 5, no AI HAT

This box doesn't use Hailo. Don't install HailoRT, and don't set `FAM_AI_HAILO`. Vision is EfficientNetV2-S int8 at 384, through ExecuTorch on the Arm CPU. Llama 3.2 1B Instruct is a second ExecuTorch program, also on the CPU. The two files aren't interchangeable, and this install has no `.hef`.

`pi/farm_ai/vision/factory.py` picks the backend at startup:

- `FAM_AI_HAILO` unset, and `pi/models/efficientnetv2_s_int8_384.pte` present: `cpu`
- the same, but the `.pte` is missing: `mock`
- `FAM_AI_HAILO=1` and HailoRT importable: `hailo`

Leave the flag unset. A health check then shows which path actually loaded.

## Install

Use 64-bit Raspberry Pi OS on a Pi 5. Copy this repo. Don't copy a `.hef`.

```bash
cd pi
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
```

Install the ExecuTorch runtime for aarch64 into that same venv. Both the classifier and Llama use it. Nothing in this setup imports HailoRT.

Export the weights on a build machine, the way `pi/EXPORT.md` describes, and copy only these two files:

- `pi/models/efficientnetv2_s_int8_384.pte`
- `pi/models/llama32_1b_instruct.pte`

The token file has to be mode `0600`, or the process exits. Raspberry Pi OS can do that. A disk mounted `noexec`, or a filesystem that forces `0777`, can't. Keep the token and the audit database off the repo:

```bash
sudo install -d -m 0700 /var/lib/fam-ai
python - <<'PY'
import secrets, pathlib
path = pathlib.Path("/var/lib/fam-ai/token")
path.write_text(secrets.token_urlsafe(32) + "\n")
path.chmod(0o600)
PY
```

Bind the Pi's own address. The service won't accept `0.0.0.0`. On the hotspot that address is `192.168.4.1`. On a home LAN, use the address from `ip -4 addr`.

```bash
export FAM_AI_HOST=192.168.4.1
export FAM_AI_PORT=8080
export FAM_AI_TOKEN_FILE=/var/lib/fam-ai/token
export FAM_AI_DB=/var/lib/fam-ai/audit.db
unset FAM_AI_HAILO
python -m farm_ai.api
```

`GET /v1/health` from a phone on that network should return `"vision_backend": "cpu"` and `"llm_backend": "executorch"`. If the `.pte` files aren't there yet, the same command still starts, and health shows `mock`. That's a wiring check, not a model run.

In the app, switch the mode to Pi, set the host and port `8080`, and paste the token. Demo mode never calls this service. The result screen names the backend, so a CPU run shows `cpu` and a missing-weight run shows `mock`.
