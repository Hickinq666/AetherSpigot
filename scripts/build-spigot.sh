#!/bin/sh
# Compile Spigot 1.8.8 avec BuildTools et le copie dans server/spigot-1.8.8.jar.
# Le jar du serveur n'est pas versionné : il contient le code de Mojang.
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
work="$root/server/buildtools"
mkdir -p "$work"
cd "$work"

if ! command -v java >/dev/null 2>&1; then
  echo "Java est introuvable. Installe Java 8 ou Java 17, puis relance."
  exit 1
fi
if ! command -v git >/dev/null 2>&1; then
  echo "git est introuvable. BuildTools clone Bukkit, CraftBukkit et Spigot."
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "curl est introuvable."
  exit 1
fi

echo "Java utilisé :"
java -version

if [ ! -f BuildTools.jar ]; then
  echo "Téléchargement de BuildTools..."
  curl -fL --retry 3 -o BuildTools.jar \
    "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"
fi

echo "Compilation de Spigot 1.8.8. Compte plusieurs minutes."
echo "Si ça casse sous Java 21, relance ce script avec Java 8 ou Java 17."
java -jar BuildTools.jar --rev 1.8.8

if [ ! -f spigot-1.8.8.jar ]; then
  echo "BuildTools n'a pas produit spigot-1.8.8.jar."
  exit 1
fi

cp -f spigot-1.8.8.jar "$root/server/spigot-1.8.8.jar"
echo "Serveur écrit : server/spigot-1.8.8.jar"
echo "Démarre-le avec : sh run.sh"
