#!/bin/sh
# Démarre AetherSpigot. Pas de « pause » ici : c'est une commande Windows.
# Depuis le dépôt : le serveur tourne dans server/ avec bundle/AetherSpigot.jar.
# Posé dans un dossier serveur : lance spigot.jar (ou AetherSpigot.jar) de ce dossier.
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
if [ -f "$root/bundle/AetherSpigot.jar" ]; then
  server="$root/server"
  mkdir -p "$server"
  if [ ! -f "$server/spigot.jar" ] || ! cmp -s "$root/bundle/AetherSpigot.jar" "$server/spigot.jar"; then
    cp "$root/bundle/AetherSpigot.jar" "$server/spigot.jar"
  fi
  jar=spigot.jar
else
  server="$root"
  jar=spigot.jar
  [ -f "$server/$jar" ] || jar=AetherSpigot.jar
fi

java=${AETHER_JAVA:-}
if [ -z "$java" ]; then
  for dir in /usr/lib/jvm/java-21-openjdk-amd64 /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-21-openjdk-arm64 /usr/lib/jvm/java-17-openjdk-arm64 /usr/lib/jvm/temurin-21-jdk-amd64 /usr/lib/jvm/temurin-17-jdk-amd64; do
    if [ -x "$dir/bin/java" ]; then
      java="$dir/bin/java"
      break
    fi
  done
fi
java=${java:-java}

if [ ! -f "$server/eula.txt" ] || ! grep -q '^eula=true' "$server/eula.txt"; then
  echo "Avant le premier démarrage, lis https://aka.ms/MinecraftEULA puis :"
  echo "  echo eula=true > $server/eula.txt"
fi

cd "$server"
exec "$java" -Xms"${AETHER_XMS:-4G}" -Xmx"${AETHER_XMX:-4G}" -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -jar "$jar" nogui
