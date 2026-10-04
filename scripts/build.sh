#!/bin/sh
# Recompile AetherSpigot depuis les sources et met le jar dans bundle/AetherSpigot.jar.
# Il faut Java 17+ et Maven. La première fois, BuildTools (Java 8) installe la dépendance
# minecraft-server 1.8.8 dans ~/.m2 : elle n'existe sur aucun dépôt Maven public.
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$root"

if [ ! -f "$HOME/.m2/repository/org/spigotmc/minecraft-server/1.8.8-SNAPSHOT/minecraft-server-1.8.8-SNAPSHOT.jar" ]; then
  java8=${AETHER_JAVA8:-}
  for dir in /usr/lib/jvm/java-8-openjdk-amd64 /usr/lib/jvm/java-8-openjdk-arm64 /usr/lib/jvm/temurin-8-jdk-amd64; do
    if [ -z "$java8" ] && [ -x "$dir/bin/java" ]; then
      java8="$dir/bin/java"
    fi
  done
  if [ -z "$java8" ]; then
    echo "minecraft-server 1.8.8 manque dans ~/.m2 et Java 8 est introuvable."
    echo "Installe-le (sudo apt install openjdk-8-jdk-headless) ou indique AETHER_JAVA8=/chemin/vers/java."
    exit 1
  fi
  mkdir -p build/buildtools
  cd build/buildtools
  [ -f BuildTools.jar ] || curl -fL -o BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
  "$java8" -jar BuildTools.jar --rev 1.8.8
  cd "$root"
fi

mvn -B clean install -Dtest="net/aether/**/*Test" -Dsurefire.failIfNoSpecifiedTests=false
mkdir -p bundle
cp AetherSpigot-Server/target/AetherSpigot.jar bundle/AetherSpigot.jar
echo "Jar prêt : bundle/AetherSpigot.jar"
