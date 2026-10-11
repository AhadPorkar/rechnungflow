#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
[ -f build/rechnungflow-0.1.0.jar ] || python3 scripts/build.py
exec java -jar build/rechnungflow-0.1.0.jar --demo "$@"
