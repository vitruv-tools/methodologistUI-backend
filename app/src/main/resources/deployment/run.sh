#!/usr/bin/env bash
# Starts the VSUM application that was built by Methodologist.
# Linux: run './run.sh' (or double-click, depending on your desktop environment).
# macOS: double-click 'run.command' instead.
set -euo pipefail

cd "$(dirname "$0")"

JAR="vsum.jar"
MIN_JAVA_VERSION=17

if [ ! -f "$JAR" ]; then
  echo "ERROR: '$JAR' was not found next to this script." >&2
  echo "       Extract the whole ZIP archive before starting the application." >&2
  exit 1
fi

if command -v java >/dev/null 2>&1; then
  JAVA_BIN="java"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
else
  echo "ERROR: No Java runtime was found." >&2
  echo "       Install Java $MIN_JAVA_VERSION or newer from https://adoptium.net" >&2
  echo "       and start this script again." >&2
  exit 1
fi

JAVA_VERSION_LINE="$("$JAVA_BIN" -version 2>&1 | head -n 1)"
JAVA_MAJOR="$(printf '%s' "$JAVA_VERSION_LINE" | sed -n 's/.*version "1\.\([0-9]*\).*/\1/p')"
if [ -z "$JAVA_MAJOR" ]; then
  JAVA_MAJOR="$(printf '%s' "$JAVA_VERSION_LINE" | sed -n 's/.*version "\([0-9]*\).*/\1/p')"
fi

if [ -n "$JAVA_MAJOR" ] && [ "$JAVA_MAJOR" -lt "$MIN_JAVA_VERSION" ]; then
  echo "ERROR: Java $MIN_JAVA_VERSION or newer is required, but found: $JAVA_VERSION_LINE" >&2
  echo "       Install a newer runtime from https://adoptium.net" >&2
  exit 1
fi

echo "Starting the VSUM application ($JAVA_VERSION_LINE)"
echo "Press Ctrl+C to stop it."
echo

# JAVA_OPTS is intentionally unquoted so that multiple options are split into arguments.
# shellcheck disable=SC2086
exec "$JAVA_BIN" ${JAVA_OPTS:-} -jar "$JAR" "$@"
