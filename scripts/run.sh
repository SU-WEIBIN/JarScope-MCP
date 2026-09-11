#!/usr/bin/env sh
set -eu

if [ -z "${JAVA_HOME:-}" ]; then
  echo "JAVA_HOME must point to JDK 21" >&2
  exit 2
fi

JAR="${1:-$(dirname "$0")/../target/java-code-intelligence-mcp-0.1.0-SNAPSHOT.jar}"
if [ "$#" -gt 0 ]; then shift; fi
exec "$JAVA_HOME/bin/java" -jar "$JAR" "$@"
