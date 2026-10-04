#!/usr/bin/env bash
# Retélécharge ViaVersion, ViaBackwards et ViaRewind, embarqués dans le jar du serveur.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
dest="$root/AetherSpigot-Server/src/main/aether/aether/plugins"
mkdir -p "$dest"
download() {
  local name="$1"
  local url="$2"
  echo "Telechargement $name"
  curl -fL --retry 3 -o "$dest/$name.embed" "$url"
}
download "ViaVersion-5.11.0.jar" "https://github.com/ViaVersion/ViaVersion/releases/download/5.11.0/ViaVersion-5.11.0.jar"
download "ViaBackwards-5.11.0.jar" "https://github.com/ViaVersion/ViaBackwards/releases/download/5.11.0/ViaBackwards-5.11.0.jar"
download "ViaRewind-4.1.3.jar" "https://github.com/ViaVersion/ViaRewind/releases/download/4.1.3/ViaRewind-4.1.3.jar"
echo "Jars dans $dest : relance scripts/build.sh pour les mettre dans le serveur."
echo "ViaVersion 5 a besoin de Java 17 ou plus."
