#!/usr/bin/env bash
# Runs one headless EST job (a main class inside the API boot jar) and returns its exit code.
# Configuration comes only from the environment. Uses JAVA_HOME or java on PATH (Java 25); SDKMAN is
# a fallback, not a requirement. Set EST_JOB_JAR to reuse a prebuilt jar instead of building one.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MAIN="${1:?usage: run-job.sh <main-class>}"

java_ok() { "${JAVA_HOME:+$JAVA_HOME/bin/}java" -version 2>&1 | grep -q 'version "25'; }
if ! java_ok && [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  set +u
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  sdk use java 25.0.4-tem >/dev/null 2>&1 || true
  set -u
fi
if ! java_ok; then
  echo "Java 25 is required (set JAVA_HOME or put java 25 on PATH)" >&2
  exit 1
fi
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

JAR="${EST_JOB_JAR:-}"
if [[ -z "$JAR" ]]; then
  "$ROOT/backend/gradlew" -p "$ROOT/backend" bootJar --no-daemon --console=plain -q >&2
  JAR="$(find "$ROOT/backend/build/libs" -name 'est-api-*.jar' ! -name '*-plain.jar' | head -n 1)"
fi
if [[ ! -f "$JAR" ]]; then
  echo "Job jar not found" >&2
  exit 1
fi

exec "$JAVA" -Dloader.main="$MAIN" -cp "$JAR" org.springframework.boot.loader.launch.PropertiesLauncher
