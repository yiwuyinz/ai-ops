#!/usr/bin/env bash
# Lightweight Maven bootstrap wrapper (no pre-installed Maven required).
# Downloads Apache Maven into tools/maven on first run, then delegates.
set -euo pipefail

MAVEN_VERSION=3.9.9
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="$ROOT/tools/maven"
MVN="$TOOLS_DIR/apache-maven-$MAVEN_VERSION/bin/mvn"

if [ ! -x "$MVN" ]; then
  echo "[mvnw] Apache Maven $MAVEN_VERSION not found, downloading (first run only)..."
  mkdir -p "$TOOLS_DIR"
  TMP_ZIP="${TMPDIR:-/tmp}/apache-maven-$MAVEN_VERSION-bin.zip"
  curl -fsSL "https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.zip" -o "$TMP_ZIP"
  unzip -q -o "$TMP_ZIP" -d "$TOOLS_DIR"
  rm -f "$TMP_ZIP"
fi

exec "$MVN" "$@"
