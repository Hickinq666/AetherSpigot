#!/bin/sh
# Démarre le practice HCF. Pas de « pause » ici : c'est une commande Windows.
# Marche depuis le dépôt (serveur dans server/) ou posé dans un dossier serveur à côté de AetherSpigot-1.8.8.jar.
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
if [ -f "$root/bundle/AetherSpigot-1.8.8.jar" ]; then
  server="$root/server"
  bundle="$root/bundle/AetherSpigot-1.8.8.jar"
else
  server="$root"
  bundle="$root/AetherSpigot-1.8.8.jar"
fi
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
  echo "  echo eula=true > $server/eula.txt"
fi

cd "$server"
# Le nom de classe est stocké en clair dans l'index du zip : présent seulement dans le jar serveur complet.
if [ ! -f spigot.jar ] || ! grep -q "net/minecraft/server/v1_8_R3/MinecraftServer.class" spigot.jar; then
  if [ ! -f "$bundle" ] && [ -f spigot.jar ] && grep -q "org/aetherspigot/launcher/Paperclip.class" spigot.jar; then
    bundle="$server/AetherSpigot-1.8.8.jar"
    mv spigot.jar "$bundle"
  fi
  echo "Préparation de spigot.jar (une seule fois)..."
  "$java" -jar "$bundle" --export spigot.jar
fi
exec "$java" -Xms"${AETHER_XMS:-4G}" -Xmx"${AETHER_XMX:-4G}" -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -jar spigot.jar nogui
