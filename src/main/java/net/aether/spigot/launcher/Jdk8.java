package net.aether.spigot.launcher;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** BuildTools refuse de compiler la 1.8.8 hors de Java 7 et 8 : on trouve ou on télécharge un JDK 8. */
final class Jdk8 {

    private static final String ADOPTIUM = "https://api.adoptium.net/v3/binary/latest/8/ga/%s/%s/jdk/hotspot/normal/eclipse";

    private Jdk8() {
    }

    static File find(File dir) throws Exception {
        String home = System.getProperty("java.home");
        if (System.getProperty("java.specification.version", "").equals("1.8")) {
            File current = javaIn(new File(home));
            if (current == null) {
                current = javaIn(new File(home).getParentFile());
            }
            if (current != null) {
                return current;
            }
        }
        String env = System.getenv("AETHER_JAVA8");
        if (env != null && !env.isEmpty()) {
            File file = new File(env);
            File java = file.isFile() ? (hasJavac(file.getParentFile().getParentFile()) ? file : null) : javaIn(file);
            if (java == null) {
                throw new Launcher.BuildException("AETHER_JAVA8 ne pointe pas vers un JDK 8 : " + env);
            }
            return java;
        }
        String[] known = {
                "/usr/lib/jvm/java-8-openjdk-amd64", "/usr/lib/jvm/java-8-openjdk-arm64",
                "/usr/lib/jvm/java-1.8.0-openjdk", "/usr/lib/jvm/java-1.8.0-openjdk-amd64",
                "/usr/lib/jvm/temurin-8-jdk-amd64", "/usr/lib/jvm/temurin-8-jdk-arm64",
        };
        for (String path : known) {
            File java = javaIn(new File(path));
            if (java != null) {
                return java;
            }
        }
        File local = search(dir);
        if (local != null) {
            return local;
        }
        download(dir);
        local = search(dir);
        if (local == null) {
            throw new Launcher.BuildException("Le JDK 8 téléchargé est illisible. Supprime " + dir.getPath() + " et relance.");
        }
        return local;
    }

    private static void download(File dir) throws Exception {
        String os = os();
        String arch = arch(os);
        boolean zip = os.equals("windows");
        File archive = new File(dir.getParentFile(), "jdk8." + (zip ? "zip" : "tar.gz"));
        Launcher.say("Téléchargement de Java 8 (Temurin, " + os + " " + arch + ") pour compiler Spigot 1.8.8...");
        Launcher.download(String.format(ADOPTIUM, os, arch), archive);
        Launcher.deleteTree(dir);
        Launcher.mkdirs(dir);
        if (zip) {
            unzip(archive, dir);
        } else {
            untar(archive, dir);
        }
        archive.delete();
    }

    private static String os() throws Launcher.BuildException {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return "windows";
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return "mac";
        }
        if (name.contains("linux")) {
            return "linux";
        }
        throw new Launcher.BuildException("Système non géré : " + name + ". Indique un JDK 8 avec AETHER_JAVA8.");
    }

    private static String arch(String os) {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return os.equals("mac") ? "x64" : "aarch64";
        }
        return "x64";
    }

    private static File search(File dir) {
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            File java = javaIn(child);
            if (java == null) {
                java = javaIn(new File(child, "Contents/Home"));
            }
            if (java != null) {
                return java;
            }
        }
        return null;
    }

    private static File javaIn(File home) {
        if (home == null || !hasJavac(home)) {
            return null;
        }
        File java = new File(home, Launcher.windows() ? "bin/java.exe" : "bin/java");
        return java.isFile() ? java : null;
    }

    private static boolean hasJavac(File home) {
        return home != null && (new File(home, "bin/javac").isFile() || new File(home, "bin/javac.exe").isFile());
    }

    private static void unzip(File archive, File dir) throws IOException {
        ZipFile zip = new ZipFile(archive);
        try {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File target = safe(dir, entry.getName());
                if (entry.isDirectory()) {
                    Launcher.mkdirs(target);
                } else {
                    Launcher.copy(zip.getInputStream(entry), target);
                }
            }
        } finally {
            zip.close();
        }
    }

    static void untar(File archive, File dir) throws IOException {
        InputStream in = new BufferedInputStream(new GZIPInputStream(new FileInputStream(archive), 65536));
        try {
            byte[] header = new byte[512];
            String longName = null;
            while (readFully(in, header)) {
                if (isZero(header)) {
                    break;
                }
                String name = longName != null ? longName : entryName(header);
                longName = null;
                char type = (char) header[156];
                long size = octal(header, 124, 12);
                int mode = (int) octal(header, 100, 8);
                if (type == 'L') {
                    longName = text(readBytes(in, size));
                    skipPadding(in, size);
                    continue;
                }
                if (type == 'x' || type == 'g') {
                    String path = paxPath(text(readBytes(in, size)));
                    skipPadding(in, size);
                    if (type == 'x') {
                        longName = path;
                    }
                    continue;
                }
                File target = safe(dir, name);
                if (type == '5') {
                    Launcher.mkdirs(target);
                } else if (type == '2') {
                    String link = string(header, 157, 100);
                    try {
                        java.nio.file.Files.createSymbolicLink(target.toPath(), new File(link).toPath());
                    } catch (Exception ignored) {
                    }
                } else if (type == '0' || type == 0) {
                    Launcher.copy(new Bounded(in, size), target);
                    skipPadding(in, size);
                    if ((mode & 0100) != 0) {
                        target.setExecutable(true, false);
                    }
                    continue;
                }
                skip(in, size);
                skipPadding(in, size);
            }
        } finally {
            in.close();
        }
    }

    private static File safe(File dir, String name) throws IOException {
        File target = new File(dir, name);
        if (!target.getCanonicalPath().startsWith(dir.getCanonicalPath())) {
            throw new IOException("Chemin refusé dans l'archive : " + name);
        }
        return target;
    }

    private static String entryName(byte[] header) {
        String name = string(header, 0, 100);
        String magic = string(header, 257, 6);
        if (magic.startsWith("ustar")) {
            String prefix = string(header, 345, 155);
            if (!prefix.isEmpty()) {
                name = prefix + "/" + name;
            }
        }
        return name;
    }

    private static String paxPath(String records) {
        for (String line : records.split("\n")) {
            int space = line.indexOf(' ');
            if (space > 0 && line.startsWith("path=", space + 1)) {
                return line.substring(space + 6);
            }
        }
        return null;
    }

    private static boolean isZero(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static String string(byte[] data, int offset, int length) {
        int end = offset;
        while (end < offset + length && data[end] != 0) {
            end++;
        }
        try {
            return new String(data, offset, end - offset, "UTF-8");
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String text(byte[] data) {
        return string(data, 0, data.length);
    }

    private static long octal(byte[] data, int offset, int length) {
        long value = 0;
        for (int i = offset; i < offset + length; i++) {
            byte b = data[i];
            if (b == 0 || b == ' ') {
                if (value != 0) {
                    break;
                }
                continue;
            }
            value = value * 8 + (b - '0');
        }
        return value;
    }

    private static boolean readFully(InputStream in, byte[] buffer) throws IOException {
        int done = 0;
        while (done < buffer.length) {
            int read = in.read(buffer, done, buffer.length - done);
            if (read == -1) {
                return false;
            }
            done += read;
        }
        return true;
    }

    private static byte[] readBytes(InputStream in, long size) throws IOException {
        byte[] data = new byte[(int) size];
        if (!readFully(in, data)) {
            throw new IOException("Archive tronquée");
        }
        return data;
    }

    private static void skip(InputStream in, long size) throws IOException {
        long left = size;
        while (left > 0) {
            long skipped = in.skip(left);
            if (skipped <= 0) {
                if (in.read() == -1) {
                    throw new IOException("Archive tronquée");
                }
                skipped = 1;
            }
            left -= skipped;
        }
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        long rest = size % 512;
        if (rest != 0) {
            skip(in, 512 - rest);
        }
    }

    /** Lit exactement {@code size} octets sans fermer l'archive. */
    private static final class Bounded extends InputStream {
        private final InputStream in;
        private long left;

        Bounded(InputStream in, long size) {
            this.in = in;
            this.left = size;
        }

        @Override
        public int read() throws IOException {
            if (left <= 0) {
                return -1;
            }
            int b = in.read();
            if (b >= 0) {
                left--;
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (left <= 0) {
                return -1;
            }
            int read = in.read(buffer, offset, (int) Math.min(length, left));
            if (read > 0) {
                left -= read;
            }
            return read;
        }

        @Override
        public void close() {
        }
    }
}
