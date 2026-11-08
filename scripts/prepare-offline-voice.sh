#!/usr/bin/env bash
set -euo pipefail

root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
runtime="$root/android/app/voice-runtime"
model="$runtime/assets/offline-voice"
mkdir -p "$model/licenses"

if command -v sha256sum >/dev/null; then
  checksum_tool=(sha256sum)
else
  checksum_tool=(shasum -a 256)
fi

fetch_verified() (
  local url=$1 checksum=$2 destination=$3 temporary
  matches() { printf '%s  %s\n' "$checksum" "$1" | "${checksum_tool[@]}" --check --status; }
  if [[ -f "$destination" ]] && matches "$destination"; then return; fi
  temporary=$(mktemp "$destination.XXXXXX")
  trap 'rm -f -- "$temporary"' EXIT
  curl -fLsS --retry 2 --connect-timeout 15 --max-time 90 "$url" -o "$temporary"
  if ! matches "$temporary"; then
    printf 'Checksum mismatch for %s\n' "$url" >&2
    exit 1
  fi
  mv -f -- "$temporary" "$destination"
)

sherpa=https://github.com/k2-fsa/sherpa-onnx/releases/download
whisper=https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/65176e2deb88badc814a94058666cadccc29b61c
raw=https://raw.githubusercontent.com

fetch_verified "$sherpa/v2.0.1/sherpa-onnx-static-link-onnxruntime-2.0.1.aar" \
  07e042d97f3a6bd5b7fe74d4eef0513dc01a714b16c15a903250095aba43a575 "$runtime/sherpa-onnx.aar"
fetch_verified "$whisper/tiny-encoder.int8.onnx" \
  d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434 "$model/tiny-encoder.int8.onnx"
fetch_verified "$whisper/tiny-decoder.int8.onnx" \
  d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925 "$model/tiny-decoder.int8.onnx"
fetch_verified "$whisper/tiny-tokens.txt" \
  b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126 "$model/tiny-tokens.txt"
fetch_verified "$sherpa/asr-models/silero_vad.onnx" \
  9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6 "$model/silero_vad.onnx"

fetch_verified "$raw/k2-fsa/sherpa-onnx/v2.0.1/LICENSE" \
  cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30 "$model/licenses/sherpa-onnx.txt"
fetch_verified "$raw/openai/whisper/v20250625/LICENSE" \
  b5d65a59060e68c4ff940e1eddfa6f94b2d68fdf58ed7f4dd57721c997e35e9d "$model/licenses/whisper.txt"
fetch_verified "$raw/snakers4/silero-vad/v6.2/LICENSE" \
  2e63e9a38b6e8fc0c7bc37ce174caca1862870856c6daf5697cfb785e925520b "$model/licenses/silero-vad.txt"
fetch_verified "$raw/microsoft/onnxruntime/v1.28.2/LICENSE" \
  2f07c72751aed99790b8a4869cf2311df85a860b22ded05fa22803587a48922c "$model/licenses/onnxruntime.txt"
fetch_verified "$raw/microsoft/onnxruntime/v1.28.2/ThirdPartyNotices.txt" \
  0e07b95f3a8d6230037707c5c4a2b554d12c4cb67369669ac255635528ffcee2 "$model/licenses/onnxruntime-third-party.txt"
