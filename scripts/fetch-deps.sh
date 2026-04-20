#!/usr/bin/env bash
# fetch-deps.sh — seed libs/ from the local Minecraft instance.
#
# The Squire Mod v4.0.0 is a MineColonies addon. MC publishes no Maven artifact
# for 1.21.1; the jar must come from a local install. This script resolves it
# from the ATM10 instance and copies into libs/, which build.gradle consumes
# via `compileOnly files('libs/...')`. libs/ is gitignored — run this script
# after cloning before the first `./gradlew build`.
#
# Adjust INSTANCE_MODS below if your modpack instance is elsewhere.

set -euo pipefail

INSTANCE_MODS="${SQUIRE_MC_MODS_DIR:-$HOME/AppData/Local/.ftba/instances/all the mods 10 - atm10/mods}"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIBS_DIR="$PROJECT_ROOT/libs"

if [ ! -d "$INSTANCE_MODS" ]; then
    echo "ERROR: mods dir not found: $INSTANCE_MODS"
    echo "Set SQUIRE_MC_MODS_DIR to point at your MineColonies-containing instance."
    exit 1
fi

mkdir -p "$LIBS_DIR"

# Required: MineColonies jar itself.
MC_JAR="$(find "$INSTANCE_MODS" -maxdepth 1 -name 'minecolonies-*-1.21.1-*.jar' | head -1)"
if [ -z "$MC_JAR" ]; then
    echo "ERROR: no minecolonies-*-1.21.1-*.jar in $INSTANCE_MODS"
    exit 1
fi
cp -v "$MC_JAR" "$LIBS_DIR/"

# Transitive compile-time deps. MC's jar references classes from these at bytecode level.
# If the compile succeeds without them, we can trim this list later.
for pattern in 'structurize-*-1.21.1-*.jar' 'blockui-*-1.21.1-*.jar' 'domum-ornamentum-*.jar'; do
    J="$(find "$INSTANCE_MODS" -maxdepth 1 -name "$pattern" | head -1)"
    if [ -n "$J" ]; then
        cp -v "$J" "$LIBS_DIR/"
    else
        echo "WARN: no match for $pattern — compile may fail if MC references it"
    fi
done

echo ""
echo "libs/ ready:"
ls -la "$LIBS_DIR"
