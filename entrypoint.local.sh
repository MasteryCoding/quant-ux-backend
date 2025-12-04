#!/bin/bash
set -e

echo "Starting Quant-UX Backend in development mode..."
echo "Watching for changes in /app/src/..."
echo ""

# Function to compile and run
compile_and_run() {
  echo "[$(date +%H:%M:%S)] Changes detected, recompiling..."
  if mvn compile -q; then
    # Kill existing Java process if running
    pkill -f "com.qux.MATC" || true
    sleep 1

    echo "[$(date +%H:%M:%S)] Compilation successful, starting server..."
    mvn exec:java -Dexec.mainClass="io.vertx.core.Starter" -Dexec.args="run com.qux.MATC -conf matc.conf" &
    echo "[$(date +%H:%M:%S)] Server started"
  else
    echo "[$(date +%H:%M:%S)] Compilation failed, waiting for fixes..."
  fi
}

# Initial compile and run
compile_and_run

# Watch for changes in source files (Docker Compose watch syncs files, entr detects and rebuilds)
# -n: non-interactive mode (required for Docker)
# -r: restart the command when files change
# -s: run in shell
find /app/src -name "*.java" -o -name "*.xml" -o -name "*.conf" | entr -n -r -s "compile_and_run"
