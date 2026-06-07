#!/usr/bin/env bash
set -euo pipefail

# Downloads small ASR smoke WAV files for the sherpa-onnx bilingual Zipformer model.
# Usage:
#   scripts/download-asr-smoke-audio.sh
#
# Output:
#   build/asr-smoke-audio/{0,1,2,3,8k}.wav

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
target_dir="${repo_root}/build/asr-smoke-audio"
base_url="https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/resolve/main/test_wavs"

mkdir -p "${target_dir}"

for wav in 0.wav 1.wav 2.wav 3.wav 8k.wav; do
  output="${target_dir}/${wav}"
  if [[ ! -f "${output}" ]]; then
    curl -L --fail --retry 3 --retry-delay 2 \
      "${base_url}/${wav}?download=true" \
      -o "${output}"
  fi
done

echo "Downloaded ASR smoke WAV files into ${target_dir}"
