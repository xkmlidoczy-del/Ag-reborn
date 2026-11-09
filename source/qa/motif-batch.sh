#!/bin/bash
# Usage: qa/motif-batch.sh <prefix> skill [skill ...]  - runs the motif probe for those skills and writes one contact sheet per skill into $MOTIF_SHEETS.
set -u
cd "$(dirname "$0")/.." || exit 1
prefix=$1; shift
export JAVA_HOME=$HOME/.local/jdks/jdk-21.0.12.1+1/Contents/Home GRADLE_USER_HOME=$HOME/.gradle-aca PATH=$HOME/.local/jdks/jdk-21.0.12.1+1/Contents/Home/bin:$PATH
find src build -name '._*' -type f -delete
qa/run-showcase.sh "$prefix" "${MOTIF_PROPS:-}" "$(python3 qa/motif-probe.py "$prefix" "$@")" | tail -1
mkdir -p "${MOTIF_SHEETS:?set MOTIF_SHEETS}"
for k in "$@"; do "${MOTIF_PY:-python3}" qa/contact-sheet.py "$prefix" "$k" "$MOTIF_SHEETS/${prefix}_$k.png"; done
grep -n "Motif for" ../tmp/aca-show-$prefix.log | head -5
