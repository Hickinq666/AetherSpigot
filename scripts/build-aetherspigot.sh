#!/bin/sh
# Construit server/AetherSpigot-1.8.8.jar : Spigot 1.8.8 (BuildTools) + patches/server
# + plugin AetherSpigot, ViaVersion et configs HCF embarqués.
# Le jar contient du code Mojang : il se construit sur ta machine et ne se versionne pas.
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
work="$root/server/buildtools"
out="$root/server/AetherSpigot-1.8.8.jar"
# Commit Spigot-Server laissé par BuildTools. Son hash change d'une machine à l'autre.
base_file="$work/aether-base"

find_java8() {
  if [ -n "${AETHER_JAVA8:-}" ]; then
    echo "$AETHER_JAVA8"
    return
  fi
  for dir in /usr/lib/jvm/java-8-openjdk-amd64 /usr/lib/jvm/java-1.8.0-openjdk-amd64 /usr/lib/jvm/java-8-openjdk-arm64 /usr/lib/jvm/temurin-8-jdk-amd64 /usr/lib/jvm/java-1.8.0; do
    if [ -x "$dir/bin/java" ] && [ -x "$dir/bin/javac" ]; then
      echo "$dir/bin/java"
      return
    fi
  done
  if command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -q 'version "1\.8'; then
    command -v java
  fi
}

for tool in git curl; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool est introuvable. Installe-le, puis relance."
    exit 1
  fi
done

java8=$(find_java8)
if [ -z "$java8" ]; then
  echo "Il faut un JDK 8 pour compiler Spigot 1.8.8."
  echo "Debian/Ubuntu : sudo apt install openjdk-8-jdk-headless"
  echo "Ou indique son chemin : AETHER_JAVA8=/chemin/vers/java sh scripts/build-aetherspigot.sh"
  exit 1
fi
java_home=$(dirname "$(dirname "$java8")")
echo "JDK de compilation : $java_home"

mkdir -p "$work"
cd "$work"
server_src="$work/Spigot/Spigot-Server"

base=""
if [ -f "$base_file" ]; then
  base=$(cat "$base_file")
fi
if [ -n "$base" ] && [ -d "$server_src/.git" ] && git -C "$server_src" cat-file -e "$base^{commit}" 2>/dev/null; then
  echo "Sources Spigot 1.8.8 déjà prêtes, BuildTools n'est pas relancé."
else
  if [ ! -f BuildTools.jar ]; then
    echo "Téléchargement de BuildTools..."
    curl -fL --retry 3 -o BuildTools.jar \
      "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"
  fi
  echo "BuildTools compile Spigot 1.8.8. Compte 5 à 15 minutes."
  JAVA_HOME="$java_home" "$java8" -Xmx2G -jar BuildTools.jar --rev 1.8.8
  base=$(git -C "$server_src" rev-parse HEAD)
  echo "$base" > "$base_file"
fi

run_mvn() {
  if [ -f "$work/apache-maven-3.9.6/bin/mvn" ]; then
    JAVA_HOME="$java_home" sh "$work/apache-maven-3.9.6/bin/mvn" "$@"
  elif command -v mvn >/dev/null 2>&1; then
    JAVA_HOME="$java_home" mvn "$@"
  else
    echo "Maven est introuvable."
    exit 1
  fi
}

cd "$server_src"
git am --abort >/dev/null 2>&1 || true
git checkout -q -f -B aetherspigot "$base"
git clean -q -fdx src
git -c user.name=AetherSpigot -c user.email=build@aether.local am -q "$root"/patches/server/*.patch

resources="$server_src/src/main/resources/aether"
mkdir -p "$resources/defaults" "$resources/plugins"
cp -f "$root"/server-config/server.properties "$root"/server-config/bukkit.yml "$root"/server-config/spigot.yml "$resources/defaults/"
for jar in "$root"/bundle/plugins/*.jar; do
  cp -f "$jar" "$resources/plugins/$(basename "$jar").embed"
done
if [ -f "$root/target/AetherSpigot.jar" ] && [ "$root/target/AetherSpigot.jar" -nt "$root/bundle/plugins/AetherSpigot.jar" ]; then
  cp -f "$root/target/AetherSpigot.jar" "$resources/plugins/AetherSpigot.jar.embed"
fi

echo "Compilation d'AetherSpigot..."
run_mvn -q -B -DskipTests clean package

built="$server_src/target/spigot-1.8.8-R0.1-SNAPSHOT.jar"
if [ ! -f "$built" ]; then
  echo "Maven n'a pas produit $built."
  exit 1
fi
cp -f "$built" "$out"
echo "Serveur écrit : server/AetherSpigot-1.8.8.jar"
echo "Démarre-le avec : sh run.sh"
