#!/usr/bin/env bash
set -euo pipefail

MOD_NAME="squire-mod-v2"
SERVER_HOST="minecraft"
SERVER_MODS="/opt/minecraft/atm10-6.4/mods"
SERVER_RCON_PASS_FILE="/opt/minecraft/atm10-6.4/.rcon-password"
CLIENT_MODS="$LOCALAPPDATA/.ftba/instances/all the mods 10 - atm10/mods"

# Note: RCON password is NOT fetched to the local machine. It stays on the
# server, read directly from $SERVER_RCON_PASS_FILE inside the remote shell
# during the restart step. This keeps it out of local process argv and logs.

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
    # Password read + mcrcon invocation happens entirely on the remote shell.
    # The password never enters argv of this local process, never crosses the
    # network as a variable, and only appears in argv on the server itself
    # (where anyone who could read /proc can also read the password file).
    ssh "$SERVER_HOST" "RCON_PASS=\$(cat '$SERVER_RCON_PASS_FILE' 2>/dev/null) && mcrcon -H 127.0.0.1 -P 25575 -p \"\$RCON_PASS\" 'stop'" || true
    echo "==> Server restart initiated (Crafty will auto-restart)"
fi
