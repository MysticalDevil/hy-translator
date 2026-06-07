#!/usr/bin/env bash
set -euo pipefail

# Downloads small OCR smoke images for the PaddleOCR PP-OCRv5 mobile runtime.
# Usage:
#   scripts/download-ocr-smoke-images.sh
#
# Output:
#   build/ocr-smoke-images/{general_ocr_rec_001,general_ocr_002}.png

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
target_dir="${repo_root}/build/ocr-smoke-images"
base_url="https://paddle-model-ecology.bj.bcebos.com/paddlex/imgs/demo_image"

mkdir -p "${target_dir}"

for image in general_ocr_rec_001.png general_ocr_002.png; do
  output="${target_dir}/${image}"
  if [[ ! -f "${output}" ]]; then
    curl -L --fail --retry 3 --retry-delay 2 \
      "${base_url}/${image}" \
      -o "${output}"
  fi
done

echo "Downloaded OCR smoke images into ${target_dir}"
