#!/bin/bash
# Launch vanilla Minecraft 1.7.10 with Vertex on this Mac (arm64).
# Uses the freshest jar in ~/vertex/build/libs (run ./gradlew build to update it),
# runtime libs cached in ~/.cache/vertex-run, and ~/.minecraft-vertex as the game dir.
set -euo pipefail

JAVA=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home/bin/java
CACHE="$HOME/.cache/vertex-run"
GD="$HOME/.minecraft-vertex"
VERTEX_JAR=$(ls -t "$HOME"/vertex/build/libs/vertex-*.jar 2>/dev/null | head -1)
LW="$HOME/.gradle/caches/modules-2/files-2.1/net.minecraft/launchwrapper/1.12/111e7bea9c968cdb3d06ef4632bf7ff0824d0f36/launchwrapper-1.12.jar"

if [ -z "$VERTEX_JAR" ]; then
    echo "No Vertex jar found - run: cd ~/vertex && ./gradlew build" >&2
    exit 1
fi

DEPS=$(tr '\n' ':' < "$CACHE/deps.txt" | sed 's/:$//')

exec "$JAVA" -Xmx2G -Djava.library.path="$CACHE/natives" "$@" \
    -cp "$VERTEX_JAR:$LW:$CACHE/minecraft-1.7.10.jar:$CACHE/lwjgl-2.9.4.jar:$CACHE/lwjgl_util-2.9.4.jar:$DEPS" \
    net.minecraft.launchwrapper.Launch --tweakClass vertex.VertexTweaker \
    --username Max --version 1.7.10-Vertex --accessToken 0 --userProperties '{}' \
    --gameDir "$GD" --assetsDir "$GD/assets"
