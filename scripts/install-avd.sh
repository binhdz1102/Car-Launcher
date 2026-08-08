#!/usr/bin/env bash

set -euo pipefail

readonly PACKAGE_NAME="com.android.car.carlauncher"
readonly EXPECTED_VERSION_CODE="1000"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly SERIAL="${1:-${ANDROID_SERIAL:-emulator-5554}}"
readonly APK_PATH="${2:-${PROJECT_DIR}/app/build/outputs/apk/debug/app-debug.apk}"
readonly ADB_BIN="${ADB_BIN:-adb}"

if ! command -v "${ADB_BIN}" >/dev/null 2>&1; then
    echo "adb was not found: ${ADB_BIN}" >&2
    exit 1
fi

if [[ ! -f "${APK_PATH}" ]]; then
    echo "APK not found; building the debug replacement first."
    (
        cd "${PROJECT_DIR}"
        bash ./gradlew :app:assembleDebug --console=plain --max-workers=2
    )
fi

if [[ "$("${ADB_BIN}" -s "${SERIAL}" get-state 2>/dev/null || true)" != "device" ]]; then
    echo "Android device is not ready: ${SERIAL}" >&2
    exit 1
fi

echo "Installing ${APK_PATH} over ${PACKAGE_NAME} on ${SERIAL}..."
"${ADB_BIN}" -s "${SERIAL}" install -r "${APK_PATH}"

readonly PACKAGE_PATH="$(
    "${ADB_BIN}" -s "${SERIAL}" shell pm path "${PACKAGE_NAME}" |
        tr -d '\r' |
        sed -n '1p'
)"
readonly VERSION_LINE="$(
    "${ADB_BIN}" -s "${SERIAL}" shell dumpsys package "${PACKAGE_NAME}" |
        tr -d '\r' |
        sed -n 's/^[[:space:]]*versionCode=\([^[:space:]]*\).*/\1/p' |
        head -n 1
)"

if [[ "${PACKAGE_PATH}" != package:/data/app/* ]]; then
    echo "The active package is not the adb-installed update: ${PACKAGE_PATH}" >&2
    exit 1
fi

if [[ "${VERSION_LINE}" != "${EXPECTED_VERSION_CODE}" ]]; then
    echo "Unexpected active versionCode: ${VERSION_LINE}" >&2
    exit 1
fi

"${ADB_BIN}" -s "${SERIAL}" shell am force-stop "${PACKAGE_NAME}"
"${ADB_BIN}" -s "${SERIAL}" shell am start -W \
    -a android.intent.action.MAIN \
    -c android.intent.category.HOME \
    -n "${PACKAGE_NAME}/.CarLauncher"

echo "Active replacement: ${PACKAGE_PATH} (versionCode=${VERSION_LINE})"
