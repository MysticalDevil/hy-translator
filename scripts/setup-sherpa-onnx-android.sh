#!/usr/bin/env bash
set -euo pipefail

# Downloads the official sherpa-onnx Android runtime and installs only jniLibs.
# Usage:
#   scripts/setup-sherpa-onnx-android.sh [version]
#
# Output:
#   third_party/sherpa-onnx-android/jniLibs/<abi>/*.so

version="${1:-1.13.2}"
archive_name="sherpa-onnx-v${version}-android.tar.bz2"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
target_dir="${repo_root}/third_party/sherpa-onnx-android"
archive_path="${target_dir}/${archive_name}"
download_url="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${version}/${archive_name}"

mkdir -p "${target_dir}"

if [[ ! -f "${archive_path}" ]]; then
  curl -L --fail "${download_url}" -o "${archive_path}"
fi

tmp_dir="$(mktemp -d)"
cleanup() {
  rm -rf "${tmp_dir}"
}
trap cleanup EXIT

tar -xjf "${archive_path}" -C "${tmp_dir}"

rm -rf "${target_dir}/jniLibs"
mkdir -p "${target_dir}"
cp -R "${tmp_dir}/jniLibs" "${target_dir}/jniLibs"

required_libs=(
  "libsherpa-onnx-jni.so"
  "libonnxruntime.so"
)

for abi in arm64-v8a x86_64; do
  for lib in "${required_libs[@]}"; do
    path="${target_dir}/jniLibs/${abi}/${lib}"
    if [[ ! -f "${path}" ]]; then
      echo "Missing ${path}" >&2
      exit 1
    fi
  done
done

cat > "${target_dir}/VERSION" <<EOF
${version}
EOF

echo "Installed sherpa-onnx Android runtime ${version} into ${target_dir}/jniLibs"
