# Exporting the models

The class order is the order of `id` in `kb/labels.json`. The `.pte` and the `.hef` must use that same order. Do not commit the weight files.

Vision is EfficientNetV2-S, int8, 384 input. Llama 3.2 1B Instruct is a separate export and stays on the Arm CPU. The Hailo HEF is only the vision network.

1. Fine-tune `efficientnet_v2_s` at 384 on these labels. The logit index is the position of that id in `kb/labels.json`.

2. Quantize to int8 and export an ExecuTorch program. `forward` must accept float32 NCHW `1x3x384x384`, already ImageNet normalized (mean 0.485, 0.456, 0.406 and std 0.229, 0.224, 0.225), and return one logit per label. Save it as `pi/models/efficientnetv2_s_int8_384.pte`. The service hashes this file once when the process starts.

3. On a build machine with the Hailo Dataflow Compiler, compile that same network to a HEF. Input is uint8 NHWC 384x384 RGB. Output logits stay in the label-file order. Save it as `pi/models/efficientnetv2_s_int8_384.hef`. Do not put Llama in the HEF.

4. Export Llama 3.2 1B Instruct with ExecuTorch, separately from the vision model. The program should expose `generate` or `forward`, accept the prompt string, and stop at 256 new tokens. Save it as `pi/models/llama32_1b_instruct.pte`. This file is not loaded when the vision backend is Hailo.

5. Point the service at other paths with `FAM_AI_CLASSIFIER_PTE`, `FAM_AI_CLASSIFIER_HEF`, and `FAM_AI_LLAMA_PTE` if the files do not sit in `pi/models/`.
