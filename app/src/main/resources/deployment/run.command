#!/usr/bin/env bash
# macOS launcher: double-click this file to start the VSUM application.
# It simply delegates to run.sh and keeps the window open if something goes wrong.

cd "$(dirname "$0")"

./run.sh "$@"
STATUS=$?

if [ $STATUS -ne 0 ]; then
  echo
  echo "The application exited with status $STATUS."
  read -r -p "Press Enter to close this window ..." _
fi

exit $STATUS
