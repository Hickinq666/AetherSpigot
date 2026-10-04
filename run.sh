#!/bin/sh
# Démarre le practice HCF depuis server/. Pas de « pause » ici : c'est une commande Windows.
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
server="$root/server"
jar="$root/bundle/AetherSpigot-1.8.8.jar"
mkdir -p "$server"

java=${AETHER_JAVA:-}
if [ -z "$java" ]; then
  for dir in /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17-openjdk-arm64 /usr/lib/jvm/temurin-17-jdk-amd64 /usr/lib/jvm/java-21-openjdk-amd64; do
    if [ -x "$dir/bin/java" ]; then
      java="$dir/bin/java"
      break
    fi
  done
fi
java=${java:-java}

if [ ! -f "$server/eula.txt" ] || ! grep -q '^eula=true' "$server/eula.txt"; then
  echo "Avant le premier démarrage, lis https://aka.ms/MinecraftEULA puis :"
  echo "  echo eula=true > server/eula.txt"
fi

cd "$server"
exec "$java" -Xms"${AETHER_XMS:-4G}" -Xmx"${AETHER_XMX:-4G}" -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -jar "$jar" nogui
