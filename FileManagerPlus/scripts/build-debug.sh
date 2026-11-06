#!/usr/bin/env sh
set -eu

if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle 8.11.1 is required. Install it or generate the Gradle wrapper first." >&2
  exit 127
fi

gradle --no-daemon --max-workers=2 :app:assembleDebug
