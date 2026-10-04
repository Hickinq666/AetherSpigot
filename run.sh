#!/bin/sh
# Démarre le serveur practice HCF. Pas de « pause » ici : c'est une commande Windows.
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
server="$root/server"
mkdir -p "$server"

manifest_value() {
  jar=$1
  key=$2
  if command -v unzip >/dev/null 2>&1; then
    unzip -p "$jar" META-INF/MANIFEST.MF 2>/dev/null \
      | tr -d '\r' \
      | sed -n "s/^$key:[[:space:]]*//p" \
      | head -n 1
    return
  fi
  python3 - "$jar" "$key" <<'PY'
import sys, zipfile
path, key = sys.argv[1], sys.argv[2] + ":"
try:
    with zipfile.ZipFile(path) as z:
        raw = z.read("META-INF/MANIFEST.MF").decode("utf-8", "replace").replace("\r", "")
except Exception:
    sys.exit(0)
lines = []
for line in raw.split("\n"):
    if line.startswith(" ") and lines:
        lines[-1] += line[1:]
    else:
        lines.append(line)
for line in lines:
    if line.startswith(key):
        sys.stdout.write(line[len(key):].strip())
        break
PY
}

find_java() {
  if [ -n "${AETHER_JAVA:-}" ]; then
    echo "$AETHER_JAVA"
    return
  fi
  for dir in /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17-openjdk-arm64 /usr/lib/jvm/temurin-17-jdk-amd64 /usr/lib/jvm/java-21-openjdk-amd64; do
    if [ -x "$dir/bin/java" ]; then
      echo "$dir/bin/java"
      return
    fi
  done
  command -v java || true
}

explain() {
  cat <<EOF
Aucun jar serveur trouvé.

AetherSpigot.jar (dans bundle/plugins) est le plugin, pas le serveur.
Le serveur, c'est server/AetherSpigot-1.8.8.jar. Il se construit sur ta machine :

  sh scripts/build-aetherspigot.sh

Il faut git, curl et un JDK 8 (sudo apt install openjdk-8-jdk-headless).
Compte 5 à 15 minutes la première fois.

EOF
  for candidate in "$server/spigot.jar" "$root/spigot.jar"; do
    if [ -f "$candidate" ]; then
      main=$(manifest_value "$candidate" Main-Class || true)
      echo "  $candidate n'est pas un serveur (Main-Class: ${main:-absente})."
    fi
  done
  exit 1
}

jar=""
for candidate in \
  "$server/AetherSpigot-1.8.8.jar" \
  "$server/spigot-1.8.8.jar" \
  "$server/spigot.jar" \
  "$root/spigot-1.8.8.jar" \
  "$root/spigot.jar"
do
  if [ -f "$candidate" ] && [ "$(manifest_value "$candidate" Main-Class || true)" = "org.bukkit.craftbukkit.Main" ]; then
    jar=$candidate
    break
  fi
done
[ -n "$jar" ] || explain

# Un Spigot classique n'embarque rien : on lui copie les configs et les plugins.
if [ "$(manifest_value "$jar" Implementation-Title || true)" != "AetherSpigot" ]; then
  mkdir -p "$server/plugins"
  for name in server.properties bukkit.yml spigot.yml; do
    if [ ! -f "$server/$name" ] && [ -f "$root/server-config/$name" ]; then
      cp "$root/server-config/$name" "$server/$name"
    fi
  done
  cp -f "$root/bundle/plugins/"*.jar "$server/plugins/"
fi

java=$(find_java)
if [ -z "$java" ]; then
  echo "Java est introuvable. Installe Java 17 (sudo apt install openjdk-17-jre-headless)."
  exit 1
fi

if [ ! -f "$server/eula.txt" ] || ! grep -q '^eula=true' "$server/eula.txt"; then
  echo "Avant le premier démarrage, lis https://aka.ms/MinecraftEULA"
  echo "puis écris eula=true dans server/eula.txt :"
  echo "  echo eula=true > server/eula.txt"
fi

xms=${AETHER_XMS:-4G}
xmx=${AETHER_XMX:-4G}
cd "$server"
echo "Démarrage : $jar"
exec "$java" -Xms"$xms" -Xmx"$xmx" -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -jar "$jar" nogui
