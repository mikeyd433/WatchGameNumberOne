#!/bin/bash
# Installs the Android SDK pieces this Wear OS project builds against, so
# ./gradlew :core:test and :app:assembleDebug work in Claude Code cloud sessions.
# Needs dl.google.com allowed in the environment's network settings.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

SDK_ROOT="${ANDROID_HOME:-/opt/android-sdk}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-13114758_latest.zip"
# Keep in step with compileSdk in app/build.gradle.kts and AGP's default build-tools.
PACKAGES=("platforms;android-35" "build-tools;34.0.0" "platform-tools")

SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  tmp="$(mktemp -d)"
  curl -sSfL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp"
fi

missing=()
for pkg in "${PACKAGES[@]}"; do
  [ -d "$SDK_ROOT/${pkg//;//}" ] || missing+=("$pkg")
done
if [ ${#missing[@]} -gt 0 ]; then
  yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses > /dev/null || true
  "$SDKMANAGER" --sdk_root="$SDK_ROOT" "${missing[@]}" > /dev/null
fi

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export ANDROID_HOME=\"$SDK_ROOT\"" >> "$CLAUDE_ENV_FILE"
fi
# Gradle also reads local.properties, which covers shells that miss the env file.
echo "sdk.dir=$SDK_ROOT" > "$CLAUDE_PROJECT_DIR/local.properties"
