#!/usr/bin/env python3
"""Fabrique bundle/AetherSpigot-1.8.8.jar, le jar serveur à distribuer.

Entrées : le serveur compilé (server/AetherSpigot-1.8.8.jar, produit par build-aetherspigot.sh)
et le server.jar 1.8.8 officiel de Mojang. Sortie : un jar qui contient le lanceur et un patch
binaire (bsdiff) entre les deux. Le code de Mojang n'y est pas : le lanceur le télécharge chez Mojang.

Usage : python3 scripts/make_server_jar.py   (il faut le paquet bsdiff4 : pip install bsdiff4)
"""
import gzip
import hashlib
import io
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

import bsdiff4.core

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SERVER = os.path.join(ROOT, "server", "AetherSpigot-1.8.8.jar")
OUTPUT = os.path.join(ROOT, "bundle", "AetherSpigot-1.8.8.jar")
VANILLA_URL = "https://launcher.mojang.com/v1/objects/5fafba3f58c40dc51b5c3ca72a98f62dfdae1db7/server.jar"
VANILLA_SHA1 = "5fafba3f58c40dc51b5c3ca72a98f62dfdae1db7"
VANILLA_CACHE = os.path.join(ROOT, "server", "mojang_1.8.8.jar")


def vanilla():
    if not os.path.isfile(VANILLA_CACHE) or sha1(VANILLA_CACHE) != VANILLA_SHA1:
        print("Téléchargement du server.jar 1.8.8 de Mojang...")
        os.makedirs(os.path.dirname(VANILLA_CACHE), exist_ok=True)
        urllib.request.urlretrieve(VANILLA_URL, VANILLA_CACHE)
        if sha1(VANILLA_CACHE) != VANILLA_SHA1:
            sys.exit("SHA-1 du jar Mojang inattendu.")
    return VANILLA_CACHE


def sha1(path):
    with open(path, "rb") as f:
        return hashlib.sha1(f.read()).hexdigest()


def old_blob(path):
    with zipfile.ZipFile(path) as z:
        names = sorted(i.filename for i in z.infolist() if not i.is_dir())
        return b"".join(z.read(n) for n in names)


def new_entries(path):
    # Les dossiers restent : log4j cherche ses plugins avec getResources("com/mojang/util").
    with zipfile.ZipFile(path) as z:
        return [(i.filename, b"" if i.is_dir() else z.read(i.filename)) for i in z.infolist()]


def gz(data):
    return gzip.compress(data, compresslevel=9, mtime=0)


def compile_launcher(classes):
    src = os.path.join(ROOT, "launcher", "src")
    sources = [os.path.join(d, f) for d, _, fs in os.walk(src) for f in fs if f.endswith(".java")]
    subprocess.run(["javac", "--release", "8", "-nowarn", "-d", classes] + sources, check=True)


def main():
    if not os.path.isfile(SERVER):
        sys.exit("server/AetherSpigot-1.8.8.jar manque. Lance d'abord : sh scripts/build-aetherspigot.sh")
    old = old_blob(vanilla())
    entries = new_entries(SERVER)
    new = b"".join(data for _, data in entries)
    print(f"Mojang : {len(old) / 1e6:.1f} Mo, AetherSpigot : {len(new) / 1e6:.1f} Mo. Calcul du patch...")
    control, diff, extra = bsdiff4.core.diff(old, new)

    control_bin = struct.pack(">i", len(control)) + b"".join(struct.pack(">qqq", *c) for c in control)
    index = io.BytesIO()
    index.write(struct.pack(">i", len(entries)))
    for name, data in entries:
        encoded = name.encode("utf-8")
        index.write(struct.pack(">H", len(encoded)) + encoded + struct.pack(">i", len(data)))
    props = (
        "output=AetherSpigot-1.8.8-server.jar\n"
        f"sha256={hashlib.sha256(new).hexdigest()}\n"
        f"vanilla.url={VANILLA_URL}\n"
        f"vanilla.sha1={VANILLA_SHA1}\n"
    )

    work = tempfile.mkdtemp()
    try:
        compile_launcher(work)
        os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
        manifest = "Manifest-Version: 1.0\nMain-Class: org.aetherspigot.launcher.Paperclip\nImplementation-Title: AetherSpigot\n\n"
        with zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED) as jar:
            jar.writestr("META-INF/MANIFEST.MF", manifest)
            for d, _, fs in os.walk(work):
                for f in fs:
                    full = os.path.join(d, f)
                    jar.write(full, os.path.relpath(full, work))
            jar.writestr("aether/patch.properties", props)
            for name, data in (("control", control_bin), ("diff", diff), ("extra", extra), ("entries", index.getvalue())):
                jar.writestr(zipfile.ZipInfo(f"aether/{name}.bin.gz"), gz(data), zipfile.ZIP_STORED)
    finally:
        shutil.rmtree(work)
    print(f"Écrit : bundle/AetherSpigot-1.8.8.jar ({os.path.getsize(OUTPUT) / 1e6:.1f} Mo)")


if __name__ == "__main__":
    main()
