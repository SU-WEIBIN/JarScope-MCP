#!/usr/bin/env sh
set -eu

if [ -z "${JAVA_HOME:-}" ]; then
  echo "JAVA_HOME must point to JDK 21" >&2
  exit 2
fi

DEFAULT_JAR="$(dirname "$0")/../java-code-intelligence-mcp.jar"
if [ ! -f "$DEFAULT_JAR" ]; then DEFAULT_JAR="$(dirname "$0")/../target/java-code-intelligence-mcp-0.1.0-SNAPSHOT.jar"; fi
JAR="${1:-$DEFAULT_JAR}"
if [ "$#" -gt 0 ]; then shift; fi
exec "$JAVA_HOME/bin/java" -jar "$JAR" "$@"
