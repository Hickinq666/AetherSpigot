#!/usr/bin/env bash
# Retélécharge ViaVersion, ViaBackwards et ViaRewind dans bundle/plugins.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
dest="$root/bundle/plugins"
mkdir -p "$dest"
download() {
  local name="$1"
  local url="$2"
  echo "Telechargement $name"
  curl -fL --retry 3 -o "$dest/$name" "$url"
}
download "ViaVersion-5.11.0.jar" "https://github.com/ViaVersion/ViaVersion/releases/download/5.11.0/ViaVersion-5.11.0.jar"
download "ViaBackwards-5.11.0.jar" "https://github.com/ViaVersion/ViaBackwards/releases/download/5.11.0/ViaBackwards-5.11.0.jar"
download "ViaRewind-4.1.3.jar" "https://github.com/ViaVersion/ViaRewind/releases/download/4.1.3/ViaRewind-4.1.3.jar"
echo "Jars dans $dest"
echo "Copie-les dans le dossier plugins/ du serveur, avec AetherSpigot.jar."
echo "ViaVersion 5 a besoin de Java 17 ou plus."
