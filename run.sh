#!/bin/sh
# Démarre le practice HCF. Ce script ne contient pas « pause » (commande Windows).
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
server="$root/server"
mkdir -p "$server/plugins"

copy_if_missing() {
  name=$1
  if [ ! -f "$server/$name" ] && [ -f "$root/server-config/$name" ]; then
    cp "$root/server-config/$name" "$server/$name"
  fi
}
copy_if_missing server.properties
copy_if_missing bukkit.yml
copy_if_missing spigot.yml

if [ -d "$root/bundle/plugins" ]; then
  cp -f "$root/bundle/plugins/"*.jar "$server/plugins/"
fi
if [ -f "$root/target/AetherSpigot.jar" ]; then
  cp -f "$root/target/AetherSpigot.jar" "$server/plugins/AetherSpigot.jar"
fi

manifest_main() {
  jar=$1
  if command -v unzip >/dev/null 2>&1; then
    unzip -p "$jar" META-INF/MANIFEST.MF 2>/dev/null \
      | tr -d '\r' \
      | sed -n 's/^Main-Class:[[:space:]]*//p' \
      | head -n 1
    return
  fi
  python3 - "$jar" <<'PY'
import sys, zipfile
path = sys.argv[1]
with zipfile.ZipFile(path) as z:
    raw = z.read("META-INF/MANIFEST.MF").decode("utf-8", "replace").replace("\r", "")
buf = ""
found = ""
for line in raw.split("\n"):
    if line.startswith(" ") and buf:
        buf += line[1:]
        continue
    if buf.startswith("Main-Class:"):
        found = buf.split(":", 1)[1].strip()
        break
    buf = line
if not found and buf.startswith("Main-Class:"):
    found = buf.split(":", 1)[1].strip()
sys.stdout.write(found)
PY
}

is_aether_plugin() {
  jar=$1
  main=$(manifest_main "$jar" || true)
  if [ "$main" = "net.aether.spigot.Bootstrap" ]; then
    return 0
  fi
  if command -v unzip >/dev/null 2>&1; then
    if unzip -p "$jar" plugin.yml 2>/dev/null | grep -q 'net.aether.spigot.AetherPlugin'; then
      return 0
    fi
  fi
  return 1
}

explain() {
  cat <<EOF
AetherSpigot.jar est le plugin. Il va dans server/plugins/.
Le serveur, c'est le jar produit par BuildTools : server/spigot-1.8.8.jar.

« no main manifest attribute, in spigot.jar » s'affiche quand java -jar
ouvre le plugin (souvent renommé spigot.jar) au lieu du serveur.
« pause: not found » vient d'un script Windows. Sous Linux, retire pause.

EOF
  for candidate in "$server/spigot.jar" "$root/spigot.jar" "$server/spigot-1.8.8.jar" "$root/spigot-1.8.8.jar"; do
    if [ -f "$candidate" ]; then
      main=$(manifest_main "$candidate" || true)
      if is_aether_plugin "$candidate"; then
        echo "  $candidate → plugin AetherSpigot (Main-Class: ${main:-absente})"
      else
        echo "  $candidate → Main-Class: ${main:-absente}"
      fi
    fi
  done
  cat <<EOF

Construis le serveur : sh scripts/build-spigot.sh
Puis relance         : sh run.sh
EOF
  exit 1
}

jar=""
for candidate in \
  "$server/spigot-1.8.8.jar" \
  "$server/spigot.jar" \
  "$root/spigot-1.8.8.jar" \
  "$root/spigot.jar"
do
  if [ -f "$candidate" ]; then
    main=$(manifest_main "$candidate" || true)
    if [ "$main" = "org.bukkit.craftbukkit.Main" ]; then
      jar=$candidate
      break
    fi
  fi
done

if [ -z "$jar" ]; then
  explain
fi

xms=${AETHER_XMS:-4G}
xmx=${AETHER_XMX:-4G}
echo "Démarrage : $jar"
echo "Plugins   : $server/plugins"
echo "Si le serveur s'arrête sur l'EULA, mets eula=true dans server/eula.txt après l'avoir lue."
exec java -Xms"$xms" -Xmx"$xmx" -jar "$jar" nogui
