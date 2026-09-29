#!/usr/bin/env bash
set -euo pipefail

PAPER_VERSION="${PAPER_VERSION:-26.3}"
PAPER_BUILD="${PAPER_BUILD:-134}"
PLUGIN_JAR="${1:-}"
WORK_DIR="${2:-build/paper-smoke}"
USER_AGENT="PinnacleAFK-CI/1.0 (https://github.com/mccreeper1318/PinnacleAFK)"

if [[ -z "$PLUGIN_JAR" ]]; then
    echo "Usage: $0 <plugin-jar> [work-dir]" >&2
    exit 2
fi

if [[ ! -f "$PLUGIN_JAR" ]]; then
    echo "Plugin JAR does not exist: $PLUGIN_JAR" >&2
    exit 2
fi

PLUGIN_JAR="$(realpath "$PLUGIN_JAR")"
WORK_DIR="$(realpath -m "$WORK_DIR")"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/plugins"

PLUGIN_VERSION="$(python3 - "$PLUGIN_JAR" <<'PY'
import re
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as archive:
    plugin_yml = archive.read("plugin.yml").decode("utf-8")

match = re.search(r"(?m)^\s*version:\s*['\"]?([^'\"\s]+)['\"]?\s*$", plugin_yml)
if match is None:
    raise SystemExit("Could not read the version from plugin.yml inside the JAR.")
print(match.group(1))
PY
)"

if [[ -z "$PLUGIN_VERSION" ]]; then
    echo "Could not determine PinnacleAFK version from the packaged plugin.yml." >&2
    exit 1
fi

BUILDS_URL="https://fill.papermc.io/v3/projects/paper/versions/${PAPER_VERSION}/builds"
BUILDS_RESPONSE="$(curl -fsSL -H "User-Agent: $USER_AGENT" "$BUILDS_URL")"

PAPER_URL="$(python3 - "$PAPER_BUILD" <<'PY' <<<"$BUILDS_RESPONSE"
import json
import sys

build = int(sys.argv[1])
builds = json.load(sys.stdin)
for candidate in builds:
    if candidate.get("id") == build and candidate.get("channel") == "BETA":
        download = candidate.get("downloads", {}).get("server:default", {})
        url = download.get("url")
        if url:
            print(url)
            raise SystemExit(0)
raise SystemExit(1)
PY
)" || {
    echo "Paper ${PAPER_VERSION} build ${PAPER_BUILD} was not found in the BETA channel." >&2
    exit 1
}

if [[ -z "$PAPER_URL" ]]; then
    echo "Paper ${PAPER_VERSION} build ${PAPER_BUILD} did not provide a server download URL." >&2
    exit 1
fi

curl -fL -H "User-Agent: $USER_AGENT" "$PAPER_URL" -o "$WORK_DIR/paper.jar"
cp "$PLUGIN_JAR" "$WORK_DIR/plugins/"
printf 'eula=true\n' > "$WORK_DIR/eula.txt"
cat > "$WORK_DIR/server.properties" <<'EOF'
online-mode=false
max-players=1
view-distance=2
simulation-distance=2
spawn-protection=0
generate-structures=false
level-type=minecraft:flat
motd=PinnacleAFK CI smoke test
EOF

COMMAND_PIPE="$WORK_DIR/console.pipe"
SERVER_LOG="$WORK_DIR/server.log"
mkfifo "$COMMAND_PIPE"
exec 3<>"$COMMAND_PIPE"

SERVER_PID=""
cleanup() {
    if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
        printf 'stop\n' >&3 || true
        wait "$SERVER_PID" || true
    fi
    exec 3>&- || true
}
trap cleanup EXIT

(
    cd "$WORK_DIR"
    java -Xms512M -Xmx1536M -jar paper.jar --nogui < console.pipe > server.log 2>&1
) &
SERVER_PID=$!

READY=false
for _ in $(seq 1 240); do
    if grep -q 'Done (' "$SERVER_LOG" 2>/dev/null; then
        READY=true
        break
    fi

    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "Paper exited before startup completed." >&2
        cat "$SERVER_LOG" >&2 || true
        wait "$SERVER_PID" || true
        SERVER_PID=""
        exit 1
    fi

    sleep 1
done

if [[ "$READY" != true ]]; then
    echo "Paper did not finish startup within 240 seconds." >&2
    cat "$SERVER_LOG" >&2 || true
    exit 1
fi

if ! grep -Fq "Enabling PinnacleAFK v${PLUGIN_VERSION}" "$SERVER_LOG"; then
    echo "PinnacleAFK ${PLUGIN_VERSION} did not report a successful enable on Paper ${PAPER_VERSION} build ${PAPER_BUILD}." >&2
    cat "$SERVER_LOG" >&2
    exit 1
fi

if grep -Eq 'Error occurred while enabling PinnacleAFK|Could not load .*PinnacleAFK|Exception.*PinnacleAFK' "$SERVER_LOG"; then
    echo "PinnacleAFK reported an error during startup." >&2
    cat "$SERVER_LOG" >&2
    exit 1
fi

printf 'pafk list\n' >&3
printf 'pafk reload\n' >&3
printf 'afk\n' >&3

COMMANDS_OK=false
for _ in $(seq 1 30); do
    if grep -Fq 'No players are currently AFK.' "$SERVER_LOG" \
        && grep -Fq 'PinnacleAFK configuration reloaded.' "$SERVER_LOG" \
        && grep -Fq 'Only players can use this command.' "$SERVER_LOG"; then
        COMMANDS_OK=true
        break
    fi
    sleep 1
done

if [[ "$COMMANDS_OK" != true ]]; then
    echo "PinnacleAFK console command smoke checks did not produce the expected responses." >&2
    cat "$SERVER_LOG" >&2
    exit 1
fi

printf 'stop\n' >&3
wait "$SERVER_PID"
SERVER_PID=""

if ! grep -Fq "Disabling PinnacleAFK v${PLUGIN_VERSION}" "$SERVER_LOG"; then
    echo "PinnacleAFK did not complete the expected disable lifecycle during shutdown." >&2
    cat "$SERVER_LOG" >&2
    exit 1
fi

trap - EXIT
exec 3>&-

echo "Paper ${PAPER_VERSION} build ${PAPER_BUILD} runtime smoke test passed for PinnacleAFK ${PLUGIN_VERSION}."
