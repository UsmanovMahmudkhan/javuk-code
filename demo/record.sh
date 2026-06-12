#!/bin/sh
# Launches Javuk's REPL for an asciinema recording.
# Usage: asciinema rec demo/javuk.cast -c "./record.sh"
set -e

JAR="target/codecrafters-claude-code.jar"
if [ ! -f "$JAR" ]; then
  echo "Building first…"
  mvn -q -B package
fi

# Pick a JDK 25 if the default 'java' is older.
JAVA_BIN="java"
exec "$JAVA_BIN" --enable-preview --enable-native-access=ALL-UNNAMED -jar "$JAR"
