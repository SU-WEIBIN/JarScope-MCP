#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
VERSION=0.1.0-SNAPSHOT
NAME="java-code-intelligence-mcp-$VERSION"
mvn -q -f "$ROOT/pom.xml" clean package
rm -rf "$ROOT/dist/$NAME"
mkdir -p "$ROOT/dist/$NAME/bin" "$ROOT/dist/$NAME/config"
cp "$ROOT/target/$NAME.jar" "$ROOT/dist/$NAME/java-code-intelligence-mcp.jar"
cp "$ROOT/scripts/run.ps1" "$ROOT/scripts/run.sh" "$ROOT/dist/$NAME/bin/"
cp "$ROOT/README.md" "$ROOT/MCP_API.md" "$ROOT/AI_AGENT_GUIDE.md" "$ROOT/THIRD_PARTY_LICENSES.md" "$ROOT/dist/$NAME/"
cp "$ROOT/config/claude-code.json.example" "$ROOT/dist/$NAME/config/"
rm -f "$ROOT/dist/$NAME.zip"
(
  cd "$ROOT/dist"
  zip -qr "$NAME.zip" "$NAME"
)
echo "Created $ROOT/dist/$NAME.zip"
