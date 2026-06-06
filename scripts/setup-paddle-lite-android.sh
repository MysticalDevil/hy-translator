#!/usr/bin/env bash
set -euo pipefail

# Downloads the Paddle Lite Android Java runtime used by the official OCR demo.
# Usage:
#   scripts/setup-paddle-lite-android.sh
#
# Output:
#   third_party/paddle-lite-android/PaddlePredictor.jar
#   third_party/paddle-lite-android/jniLibs/<abi>/libpaddle_lite_jni.so

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
target_dir="${repo_root}/third_party/paddle-lite-android"
archive_path="${target_dir}/paddle_lite_libs_v2_10_rc.tar.gz"
download_url="https://paddlelite-demo.bj.bcebos.com/libs/android/paddle_lite_libs_v2_10_rc.tar.gz"

mkdir -p "${target_dir}"

if [[ ! -f "${archive_path}" ]]; then
  curl -L --fail "${download_url}" -o "${archive_path}"
fi

tmp_dir="$(mktemp -d)"
cleanup() {
  rm -rf "${tmp_dir}"
}
trap cleanup EXIT

tar -xzf "${archive_path}" -C "${tmp_dir}"

rm -rf "${target_dir}/jniLibs"
mkdir -p "${target_dir}/jniLibs"
cp "${tmp_dir}/java/PaddlePredictor.jar" "${target_dir}/PaddlePredictor.jar"

for abi in arm64-v8a armeabi-v7a; do
  mkdir -p "${target_dir}/jniLibs/${abi}"
  cp "${tmp_dir}/java/libs/${abi}/libpaddle_lite_jni.so" "${target_dir}/jniLibs/${abi}/"
  cp "${tmp_dir}/java/libs/${abi}/libc++_shared.so" "${target_dir}/jniLibs/${abi}/"
done

for path in \
  "${target_dir}/PaddlePredictor.jar" \
  "${target_dir}/jniLibs/arm64-v8a/libpaddle_lite_jni.so" \
  "${target_dir}/jniLibs/arm64-v8a/libc++_shared.so"; do
  if [[ ! -f "${path}" ]]; then
    echo "Missing ${path}" >&2
    exit 1
  fi
done

echo "Installed Paddle Lite Android runtime into ${target_dir}"
