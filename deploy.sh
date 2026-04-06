#!/usr/bin/env bash
set -euo pipefail

MOD_NAME="squire-mod-v2"
SERVER_HOST="minecraft"
SERVER_MODS="/opt/minecraft/atm10-6.4/mods"
CLIENT_MODS="$LOCALAPPDATA/.ftba/instances/all the mods 10 - atm10/mods"
RCON_PASS=$(ssh "$SERVER_HOST" 'cat /opt/minecraft/atm10-6.4/.rcon-password 2>/dev/null')

# Build
echo "==> Building..."
./gradlew build
JAR=$(find build/libs -name "${MOD_NAME}-*.jar" ! -name "*-sources.jar" | head -1)
if [ -z "$JAR" ]; then
    echo "ERROR: No build artifact found"
    exit 1
fi
echo "==> Built: $JAR"

# Remove old versions from server
echo "==> Deploying to server ($SERVER_HOST)..."
ssh "$SERVER_HOST" "rm -f ${SERVER_MODS}/${MOD_NAME}-*.jar"
scp "$JAR" "${SERVER_HOST}:${SERVER_MODS}/"
ssh "$SERVER_HOST" "chown crafty:crafty ${SERVER_MODS}/$(basename "$JAR")"

# Remove old versions from client
echo "==> Deploying to client..."
rm -f "${CLIENT_MODS}/${MOD_NAME}-"*.jar
cp "$JAR" "${CLIENT_MODS}/"

echo "==> Deployed $(basename "$JAR") to server and client"

# Restart server if requested
if [ "${1:-}" = "--restart" ]; then
    echo "==> Restarting server..."
    ssh "$SERVER_HOST" "mcrcon -H 127.0.0.1 -P 25575 -p '${RCON_PASS}' 'stop'" || true
    echo "==> Server restart initiated (Crafty will auto-restart)"
fi
