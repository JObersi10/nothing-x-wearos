#!/usr/bin/env bash
# Captures a filtered logcat (app tags only — NothingX, NothingXRelay — plus
# fatal crashes) to a fresh, timestamped file under logs/, and also updates
# logs/latest-log.txt so the newest capture always has a stable name.
#
# Usage:
#   ./scripts/capture-log.sh            # whichever single device/emulator adb sees
#   ./scripts/capture-log.sh <SERIAL>   # a specific device — see `adb devices`
#
# Run this once per device you need logs from. The relay path is split
# across two processes on two different devices — DirectRfcommTransport/
# EarbudsConnectionHolder/WearRelayTransport log on the WATCH, PhoneRelayService/
# AutoRelayReceiver log on the PHONE — so debugging the phone relay usually
# means running this twice, once with the watch's serial and once with the
# phone's (`adb devices` lists both once each is connected/paired for adb).
#
# Ctrl+C stops the capture; the file is already flushed and ready to send.
set -euo pipefail

cd "$(dirname "$0")/.."

SERIAL="${1:-}"
ADB=(adb)
if [ -n "$SERIAL" ]; then
  ADB=(adb -s "$SERIAL")
fi

mkdir -p logs

"${ADB[@]}" logcat -c

TS="$(date +%Y%m%d-%H%M%S)"
OUT="logs/${TS}-log.txt"
LATEST="logs/latest-log.txt"

echo "Capturing filtered logcat to $OUT (and $LATEST)."
echo "Now go do the thing you're testing on the device — connect, tap Buds (phone), etc."
echo "Press Ctrl+C here when you're done."
echo

"${ADB[@]}" logcat NothingX:V NothingXRelay:V AndroidRuntime:E *:S | tee "$OUT" "$LATEST"
