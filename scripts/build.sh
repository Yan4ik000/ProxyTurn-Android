#!/usr/bin/env bash
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -t 1 ]]; then
  T=$'\033[1;96m'; A=$'\033[1;95m'; G=$'\033[1;92m'; X=$'\033[1;91m'; D=$'\033[90m'; Z=$'\033[0m'
else
  T= A= G= X= D= Z=
fi

usage() {
  printf '\n%sUsage%s\n' "$T" "$Z"
  printf '  build.sh %s-a%s | %s--all%s\n' "$A" "$Z" "$A" "$Z"
  printf '  build.sh %s-c%s | %s--client%s <arm64-v8a|armeabi-v7a|x86_64>\n' "$A" "$Z" "$A" "$Z"
  printf '  build.sh %s-s%s | %s--server%s <amd64|arm64>\n' "$A" "$Z" "$A" "$Z"
  printf '  build.sh %s-f%s | %s--fast%s <arm64-v8a|armeabi-v7a|x86_64>\n' "$A" "$Z" "$A" "$Z"
  printf '  build.sh %s-h%s | %s--help%s\n\n' "$A" "$Z" "$A" "$Z"
}

clear_screen() {
  [[ -t 1 ]] && clear
}

resolve_sdk() {
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" candidate
  if [[ -z "$sdk" && -f "$ROOT/local.properties" ]]; then
    sdk="$(grep -E '^sdk\.dir=' "$ROOT/local.properties" | head -n1 | cut -d= -f2- || true)"
    sdk="${sdk//\\:/:}"
  fi
  if [[ -z "$sdk" || ! -d "$sdk" ]]; then
    sdk=""
    for candidate in "$HOME/AppData/Local/Android/Sdk" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" /opt/android-sdk /usr/lib/android-sdk; do
      [[ -d "$candidate" ]] && { sdk="$candidate"; break; }
    done
  fi
  printf '%s' "$sdk"
}

header() {
  clear_screen
  printf '\n  %sWDTT BUILD%s %s/%s %s%s%s\n' "$T" "$Z" "$D" "$Z" "$A" "$1" "$Z"
  printf '  %s================================================%s\n\n' "$D" "$Z"
}

android() {
  local abi="$1" arch prefix api ndk sdk host cc out go_version major minor flags candidate wrapper rc
  api="${ANDROID_NATIVE_API_LEVEL:-28}"
  case "$abi" in
    arm64-v8a) arch=arm64; prefix=aarch64-linux-android ;;
    armeabi-v7a) arch=arm; prefix=armv7a-linux-androideabi ;;
    x86_64) arch=amd64; prefix=x86_64-linux-android ;;
    *) printf '  %sUnsupported Android ABI: %s%s\n' "$X" "$abi" "$Z" >&2; return 2 ;;
  esac
  ndk="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
  sdk="$(resolve_sdk)"
  if [[ -z "$ndk" && -n "$sdk" && -d "$sdk/ndk" ]]; then
    ndk="$(find "$sdk/ndk" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort | tail -n1 || true)"
  fi
  if [[ -z "$ndk" ]]; then
    for candidate in "$HOME/Downloads/android-ndk-r29" "$HOME/Downloads/android-ndk-r28" "$HOME/Downloads/android-ndk-r27" /opt/android-ndk; do
      [[ -d "$candidate/toolchains/llvm/prebuilt" ]] && { ndk="$candidate"; break; }
    done
  fi
  if [[ -z "$ndk" || ! -d "$ndk/toolchains/llvm/prebuilt" ]]; then
    printf '  %sAndroid NDK was not found. Set ANDROID_NDK_HOME or ANDROID_NDK_ROOT.%s\n' "$X" "$Z" >&2
    return 1
  fi
  host=linux-x86_64
  [[ "$OSTYPE" == darwin* ]] && host=darwin-x86_64
  [[ "$OSTYPE" == msys* || "$OSTYPE" == cygwin* ]] && host=windows-x86_64
  cc="$ndk/toolchains/llvm/prebuilt/$host/bin/${prefix}${api}-clang"
  [[ ! -x "$cc" && -x "$cc.cmd" ]] && cc="$cc.cmd"
  if [[ ! -x "$cc" ]]; then
    printf '  %sAndroid NDK compiler was not found.%s\n' "$X" "$Z" >&2
    return 1
  fi
  wrapper=""
  if [[ "$OSTYPE" == msys* || "$OSTYPE" == cygwin* ]]; then
    wrapper="$(mktemp "${TMPDIR:-/tmp}/wdtt-clang.XXXXXX.cmd")"
    printf '@echo off\r\ncall "%s" %%*\r\n' "$(cygpath -w "$cc")" > "$wrapper"
    cc="$(cygpath -w "$wrapper")"
  elif [[ "$cc" == *' '* ]]; then
    wrapper="$(mktemp "${TMPDIR:-/tmp}/wdtt-clang.XXXXXX")"
    printf '%s\n' '#!/usr/bin/env bash' "exec \"$cc\" \"\$@\"" > "$wrapper"
    chmod +x "$wrapper"
    cc="$wrapper"
  fi
  out="$ROOT/app/src/main/jniLibs/$abi"
  mkdir -p "$out"
  command -v go >/dev/null || { printf '  %sGo was not found in PATH.%s\n' "$X" "$Z" >&2; return 1; }
  go_version="$(go version | awk '{print $3}' | sed 's/^go//')"
  major="${go_version%%.*}"; minor="${go_version#*.}"; minor="${minor%%.*}"
  flags='-s -w'
  if (( major > 1 || (major == 1 && minor >= 23) )); then flags='-s -w -checklinkname=0'; fi
  if [[ "$abi" == armeabi-v7a ]]; then
    (cd "$ROOT/app/src/main/assets/android-client" && go mod download && GOOS=android GOARCH="$arch" GOARM=7 CGO_ENABLED=1 CC="$cc" go build -trimpath -ldflags="$flags" -o "$out/libclient.so" .)
  else
    (cd "$ROOT/app/src/main/assets/android-client" && go mod download && GOOS=android GOARCH="$arch" CGO_ENABLED=1 CC="$cc" go build -trimpath -ldflags="$flags" -o "$out/libclient.so" .)
  fi
  rc=$?
  [[ -z "$wrapper" ]] || rm -f "$wrapper"
  [[ $rc -eq 0 ]] || return "$rc"
  printf '  %sAndroid %s: OK%s\n' "$G" "$abi" "$Z"
}

linux() {
  local abi="$1" name
  case "$abi" in amd64) name=server ;; arm64) name=server-arm64 ;; *) printf '  %sUnsupported Linux ABI: %s%s\n' "$X" "$abi" "$Z" >&2; return 2 ;; esac
  (cd "$ROOT/app/src/main/assets/linux-server" && GOOS=linux GOARCH="$abi" CGO_ENABLED=0 go build -ldflags='-s -w' -o "$ROOT/app/src/main/assets/$name" .) || return 1
  printf '  %sLinux %s: OK%s\n' "$G" "$abi" "$Z"
}

copy_apk() {
  local source="$1" target="$2" path
  path="$ROOT/app/build/outputs/apk/release/$source"
  [[ -f "$path" ]] || { printf '  %sAPK was not generated: %s%s\n' "$X" "$source" "$Z" >&2; return 1; }
  cp "$path" "$ROOT/app/release/$target"
  printf '  %s[OK]%s %s %s[%s bytes]%s\n' "$G" "$Z" "$target" "$D" "$(wc -c < "$ROOT/app/release/$target")" "$Z"
}

apk() {
  local fast="${1:-}" gradle sdk
  if [[ -z "$fast" ]]; then
    for abi in arm64-v8a armeabi-v7a x86_64; do [[ -f "$ROOT/app/src/main/jniLibs/$abi/libclient.so" ]] || { printf '  %sMissing Android library: %s%s\n' "$X" "$abi" "$Z" >&2; return 1; }; done
  fi
  printf '\n  %sAssembling signed Release APKs...%s\n' "$T" "$Z"
  sdk="$(resolve_sdk)"
  [[ -n "$sdk" ]] || { printf '  %sAndroid SDK was not found. Set ANDROID_HOME.%s\n' "$X" "$Z" >&2; return 1; }
  export ANDROID_HOME="$sdk"
  unset ANDROID_SDK_ROOT
  gradle=./gradlew
  [[ "$OSTYPE" == msys* || "$OSTYPE" == cygwin* ]] && gradle=./gradlew.bat
  (cd "$ROOT" && "$gradle" assembleRelease --no-daemon) || return 1
  printf '\n'
  mkdir -p "$ROOT/app/release"
  if [[ -z "$fast" ]]; then
    copy_apk app-universal-release.apk WDTT-universal.apk && copy_apk app-arm64-v8a-release.apk WDTT-arm64-v8a.apk && copy_apk app-armeabi-v7a-release.apk WDTT-armeabi-v7a.apk && copy_apk app-x86_64-release.apk WDTT-x86_64.apk || return 1
  else
    copy_apk "app-$fast-release.apk" "WDTT-$fast-fast-test.apk" || return 1
  fi
  printf '  %sSigned APK output: app/release%s\n' "$G" "$Z"
}

full() { header 'Full Release'; android arm64-v8a && android armeabi-v7a && android x86_64 && linux amd64 && linux arm64 && apk; }
client() { header "Android Client Library: $1"; android "$1"; }
server() { header "Linux Server: $1"; linux "$1"; }
fast() { header "Fast Build: $1"; android "$1" && apk "$1"; }

pick_android() {
  local choice
  while true; do
    clear_screen
    printf '\n  %s%s%s\n  %s----------------------------------------%s\n\n' "$T" "$1" "$Z" "$D" "$Z"
    printf '  %s[1]%s arm64-v8a\n  %s[2]%s armeabi-v7a\n  %s[3]%s x86_64\n\n  %s[4] Back%s\n\n' "$A" "$Z" "$A" "$Z" "$A" "$Z" "$D" "$Z"
    read -r -p 'Select Android ABI: ' choice
    case "$choice" in 1) ABI=arm64-v8a; return 0 ;; 2) ABI=armeabi-v7a; return 0 ;; 3) ABI=x86_64; return 0 ;; 4) return 1 ;; esac
  done
}

pick_linux() {
  local choice
  while true; do
    clear_screen
    printf '\n  %s%s%s\n  %s----------------------------------------%s\n\n' "$T" "$1" "$Z" "$D" "$Z"
    printf '  %s[1]%s linux-amd64\n  %s[2]%s linux-arm64\n\n  %s[3] Back%s\n\n' "$A" "$Z" "$A" "$Z" "$D" "$Z"
    read -r -p 'Select Linux ABI: ' choice
    case "$choice" in 1) ABI=amd64; return 0 ;; 2) ABI=arm64; return 0 ;; 3) return 1 ;; esac
  done
}

menu() {
  local choice rc
  while true; do
    clear_screen
    printf '\n  %sWDTT BUILD%s\n  %s================================================%s\n\n' "$T" "$Z" "$D" "$Z"
    printf '  %s[1]%s Build Full Release\n  %s[2]%s Build Android Client Library\n  %s[3]%s Build Linux Server\n  %s[4]%s Fast Build\n\n  %s[0] Exit%s\n\n' "$A" "$Z" "$A" "$Z" "$A" "$Z" "$A" "$Z" "$D" "$Z"
    read -r -p 'Select an option: ' choice
    case "$choice" in
      0) return 0 ;;
      1) full; rc=$? ;;
      2) if pick_android 'Build Android Client Library'; then client "$ABI"; rc=$?; else continue; fi ;;
      3) if pick_linux 'Build Linux Server'; then server "$ABI"; rc=$?; else continue; fi ;;
      4) if pick_android 'Fast Build'; then fast "$ABI"; rc=$?; else continue; fi ;;
      *) continue ;;
    esac
    [[ "$choice" != 1 && "$choice" != 2 && "$choice" != 3 && "$choice" != 4 ]] && continue
    if [[ $rc -eq 0 ]]; then
      printf '\n  %sCompleted successfully.%s\n\n' "$G" "$Z"
    else
      printf '\n  %sBuild failed with exit code %s.%s\n\n' "$X" "$rc" "$Z"
    fi
    read -r -p 'Press any key to return to the menu...' -n 1
  done
}

fail_missing_value() {
  printf '%sError: option "%s" requires a value. Expected: %s.%s\n' "$X" "$1" "$2" "$Z"
  usage
  exit 2
}

fail_unexpected_argument() {
  printf '%sError: unexpected argument "%s" after option "%s".%s\n' "$X" "$2" "$1" "$Z"
  usage
  exit 2
}

fail_invalid_value() {
  printf '%sError: invalid value "%s" for option "%s". Expected: %s.%s\n' "$X" "$2" "$1" "$3" "$Z"
  usage
  exit 2
}

valid_android_abi() {
  [[ "$1" == arm64-v8a || "$1" == armeabi-v7a || "$1" == x86_64 ]]
}

valid_linux_abi() {
  [[ "$1" == amd64 || "$1" == arm64 ]]
}

case "${1:-}" in
  '') menu ;;
  -h|--help|'-?'|/?) usage ;;
  -a|--all|--full) [[ $# -eq 1 ]] || fail_unexpected_argument "$1" "$2"; full ;;
  -c|--client) [[ $# -ge 2 ]] || fail_missing_value "$1" 'arm64-v8a, armeabi-v7a, or x86_64'; [[ $# -eq 2 ]] || fail_unexpected_argument "$1" "$3"; valid_android_abi "$2" || fail_invalid_value "$1" "$2" 'arm64-v8a, armeabi-v7a, or x86_64'; client "$2" ;;
  -s|--server) [[ $# -ge 2 ]] || fail_missing_value "$1" 'amd64 or arm64'; [[ $# -eq 2 ]] || fail_unexpected_argument "$1" "$3"; valid_linux_abi "$2" || fail_invalid_value "$1" "$2" 'amd64 or arm64'; server "$2" ;;
  -f|--fast) [[ $# -ge 2 ]] || fail_missing_value "$1" 'arm64-v8a, armeabi-v7a, or x86_64'; [[ $# -eq 2 ]] || fail_unexpected_argument "$1" "$3"; valid_android_abi "$2" || fail_invalid_value "$1" "$2" 'arm64-v8a, armeabi-v7a, or x86_64'; fast "$2" ;;
  *) printf '%sError: unknown option or command "%s".%s\n' "$X" "$1" "$Z"; usage; exit 2 ;;
esac
