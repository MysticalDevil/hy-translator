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
version="v2.14-rc"
base_url="https://github.com/PaddlePaddle/Paddle-Lite/releases/download/${version}"

mkdir -p "${target_dir}"

sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "${sdk_dir}" && -f "${repo_root}/local.properties" ]]; then
  sdk_dir="$(awk -F= '/^sdk.dir=/ { print $2; exit }' "${repo_root}/local.properties")"
fi
if [[ -z "${sdk_dir}" || ! -d "${sdk_dir}/ndk" ]]; then
  echo "Android SDK with NDK was not found. Set ANDROID_HOME or sdk.dir in local.properties." >&2
  exit 1
fi

ndk_dir="$(find "${sdk_dir}/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
if [[ -z "${ndk_dir}" ]]; then
  echo "No Android NDK installation found under ${sdk_dir}/ndk" >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
cleanup() {
  rm -rf "${tmp_dir}"
}
trap cleanup EXIT

rm -rf "${target_dir}/jniLibs"
mkdir -p "${target_dir}/jniLibs"

install_abi() {
  local abi="$1"
  local archive_name="$2"
  local root_name="$3"
  local libcxx_triple="$4"
  local archive_path="${target_dir}/${archive_name}"

  if [[ ! -f "${archive_path}" ]]; then
    curl -L --fail --retry 3 --retry-delay 2 \
      "${base_url}/${archive_name}" \
      -o "${archive_path}"
  fi

  tar -xzf "${archive_path}" -C "${tmp_dir}"
  mkdir -p "${target_dir}/jniLibs/${abi}"
  cp "${tmp_dir}/${root_name}/java/so/libpaddle_lite_jni.so" \
    "${target_dir}/jniLibs/${abi}/"
  cp "${ndk_dir}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/${libcxx_triple}/libc++_shared.so" \
    "${target_dir}/jniLibs/${abi}/"

  if [[ ! -f "${target_dir}/PaddlePredictor.jar" ]]; then
    cp "${tmp_dir}/${root_name}/java/jar/PaddlePredictor.jar" \
      "${target_dir}/PaddlePredictor.jar"
  fi
}

install_abi \
  "arm64-v8a" \
  "inference_lite_lib.android.armv8.clang.c%2B%2B_shared.with_extra.tar.gz" \
  "inference_lite_lib.android.armv8.clang.c++_shared.with_extra" \
  "aarch64-linux-android"

install_abi \
  "armeabi-v7a" \
  "inference_lite_lib.android.armv7.clang.c%2B%2B_shared.with_extra.tar.gz" \
  "inference_lite_lib.android.armv7.clang.c++_shared.with_extra" \
  "arm-linux-androideabi"

for path in \
  "${target_dir}/PaddlePredictor.jar" \
  "${target_dir}/jniLibs/arm64-v8a/libpaddle_lite_jni.so" \
  "${target_dir}/jniLibs/arm64-v8a/libc++_shared.so"; do
  if [[ ! -f "${path}" ]]; then
    echo "Missing ${path}" >&2
    exit 1
  fi
done

echo "Installed Paddle Lite Android ${version} runtime into ${target_dir}"
