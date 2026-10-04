package org.aetherspigot.launcher;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Jar AetherSpigot à lancer avec {@code java -jar}. Il contient Spigot, CraftBukkit et AetherSpigot
 * compilés, sous forme de patch binaire contre le server.jar 1.8.8 de Mojang. Le jar de Mojang est
 * pris sur ses serveurs officiels, vérifié, puis patché en quelques secondes.
 */
public final class Paperclip {

    private static final String TAG = "[AetherSpigot] ";

    private Paperclip() {
    }

    public static void main(String[] args) {
        try {
            Properties meta = new Properties();
            InputStream in = resource("aether/patch.properties");
            try {
                meta.load(in);
            } finally {
                in.close();
            }
            File cache = new File("cache").getAbsoluteFile();
            File server = new File(cache, meta.getProperty("output"));
            File stamp = new File(cache, meta.getProperty("output") + ".sha256");
            String expected = meta.getProperty("sha256");
            if (!server.isFile() || !stamp.isFile() || !expected.equals(read(stamp).trim())) {
                File vanilla = vanilla(cache, meta);
                say("Préparation du serveur AetherSpigot 1.8.8...");
                long started = System.currentTimeMillis();
                patch(vanilla, server, expected);
                write(stamp, expected + "\n");
                say("Serveur prêt en " + (System.currentTimeMillis() - started) / 1000.0 + " s.");
            }
            System.exit(run(server, args));
        } catch (Exception ex) {
            System.out.println(TAG + "Démarrage impossible : " + ex.getMessage());
            System.exit(1);
        }
    }

    private static File vanilla(File cache, Properties meta) throws Exception {
        File jar = new File(cache, "mojang_1.8.8.jar");
        String sha1 = meta.getProperty("vanilla.sha1");
        if (jar.isFile() && sha1.equals(hash(jar, "SHA-1"))) {
            return jar;
        }
        mkdirs(cache);
        say("Téléchargement du server.jar 1.8.8 officiel de Mojang...");
        File part = new File(cache, "mojang_1.8.8.jar.part");
        download(meta.getProperty("vanilla.url"), part);
        String got = hash(part, "SHA-1");
        if (!sha1.equals(got)) {
            part.delete();
            throw new IOException("le jar de Mojang ne correspond pas (SHA-1 " + got + ").");
        }
        jar.delete();
        if (!part.renameTo(jar)) {
            throw new IOException("impossible d'écrire " + jar.getPath());
        }
        return jar;
    }

    static void patch(File vanilla, File server, String expected) throws Exception {
        byte[] old = oldBlob(vanilla);
        long[] control = longs(resource("aether/control.bin.gz"));
        byte[] diff = bytes(resource("aether/diff.bin.gz"));
        byte[] extra = bytes(resource("aether/extra.bin.gz"));
        long size = 0;
        for (int i = 0; i < control.length; i += 3) {
            size += control[i] + control[i + 1];
        }
        byte[] out = new byte[(int) size];
        int newPos = 0;
        long oldPos = 0;
        int diffPos = 0;
        int extraPos = 0;
        for (int i = 0; i < control.length; i += 3) {
            int x = (int) control[i];
            int y = (int) control[i + 1];
            long z = control[i + 2];
            for (int j = 0; j < x; j++) {
                long o = oldPos + j;
                int base = o >= 0 && o < old.length ? old[(int) o] : 0;
                out[newPos + j] = (byte) (diff[diffPos + j] + base);
            }
            newPos += x;
            diffPos += x;
            oldPos += x;
            System.arraycopy(extra, extraPos, out, newPos, y);
            newPos += y;
            extraPos += y;
            oldPos += z;
        }
        String got = hex(MessageDigest.getInstance("SHA-256").digest(out));
        if (!got.equals(expected)) {
            throw new IOException("le patch ne donne pas le bon serveur. Supprime le dossier cache/ et relance.");
        }
        writeJar(out, server);
    }

    /** Les entrées du jar Mojang triées par nom, mises bout à bout : la base du patch. */
    static byte[] oldBlob(File vanilla) throws IOException {
        ZipFile zip = new ZipFile(vanilla);
        try {
            List<String> names = new ArrayList<String>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    names.add(entry.getName());
                }
            }
            Collections.sort(names);
            ByteArrayOutputStream blob = new ByteArrayOutputStream(24 << 20);
            for (String name : names) {
                InputStream in = zip.getInputStream(zip.getEntry(name));
                try {
                    pipe(in, blob);
                } finally {
                    in.close();
                }
            }
            return blob.toByteArray();
        } finally {
            zip.close();
        }
    }

    private static void writeJar(byte[] blob, File server) throws IOException {
        List<String> names = new ArrayList<String>();
        List<Integer> sizes = new ArrayList<Integer>();
        DataInputStream index = new DataInputStream(new BufferedInputStream(new GZIPInputStream(resource("aether/entries.bin.gz"))));
        try {
            int count = index.readInt();
            for (int i = 0; i < count; i++) {
                names.add(index.readUTF());
                sizes.add(index.readInt());
            }
        } finally {
            index.close();
        }
        mkdirs(server.getAbsoluteFile().getParentFile());
        File part = new File(server.getPath() + ".part");
        ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(part), 1 << 16));
        try {
            int pos = 0;
            for (int i = 0; i < names.size(); i++) {
                ZipEntry entry = new ZipEntry(names.get(i));
                entry.setTime(0L);
                zip.putNextEntry(entry);
                zip.write(blob, pos, sizes.get(i));
                zip.closeEntry();
                pos += sizes.get(i);
            }
        } finally {
            zip.close();
        }
        server.delete();
        if (!part.renameTo(server)) {
            throw new IOException("impossible d'écrire " + server.getPath());
        }
    }

    static int run(File server, String[] args) throws Exception {
        List<String> command = new ArrayList<String>();
        command.add(new File(System.getProperty("java.home"), "bin/java").getPath());
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (!arg.startsWith("-agentlib:") && !arg.startsWith("-javaagent:")) {
                command.add(arg);
            }
        }
        command.add("-jar");
        command.add(server.getPath());
        command.addAll(args.length == 0 ? Arrays.asList("nogui") : Arrays.asList(args));
        final Process process = new ProcessBuilder(command).inheritIO().start();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    process.exitValue();
                } catch (IllegalThreadStateException alive) {
                    process.destroy();
                    try {
                        process.waitFor();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }));
        return process.waitFor();
    }

    private static long[] longs(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)));
        try {
            int count = in.readInt();
            long[] values = new long[count * 3];
            for (int i = 0; i < values.length; i++) {
                values[i] = in.readLong();
            }
            return values;
        } finally {
            in.close();
        }
    }

    private static byte[] bytes(InputStream raw) throws IOException {
        InputStream in = new GZIPInputStream(raw, 1 << 16);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
            pipe(in, out);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static InputStream resource(String name) throws IOException {
        InputStream in = Paperclip.class.getClassLoader().getResourceAsStream(name);
        if (in == null) {
            throw new IOException(name + " manque dans le jar.");
        }
        return in;
    }

    private static void download(String url, File target) throws IOException {
        String current = url;
        for (int hop = 0; hop < 8; hop++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent", "AetherSpigot");
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400 && connection.getHeaderField("Location") != null) {
                current = new URL(new URL(current), connection.getHeaderField("Location")).toString();
                connection.disconnect();
                continue;
            }
            if (status != 200) {
                connection.disconnect();
                throw new IOException("HTTP " + status + " sur " + current);
            }
            InputStream in = connection.getInputStream();
            OutputStream out = new FileOutputStream(target);
            try {
                pipe(in, out);
            } finally {
                in.close();
                out.close();
            }
            return;
        }
        throw new IOException("trop de redirections pour " + url);
    }

    private static String hash(File file, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        InputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } finally {
            in.close();
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] data) {
        StringBuilder text = new StringBuilder(data.length * 2);
        for (byte b : data) {
            text.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return text.toString();
    }

    private static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1 << 16];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private static String read(File file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = new FileInputStream(file);
        try {
            pipe(in, out);
        } finally {
            in.close();
        }
        return out.toString("UTF-8");
    }

    private static void write(File file, String text) throws IOException {
        OutputStream out = new FileOutputStream(file);
        try {
            out.write(text.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private static void mkdirs(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("impossible de créer " + dir.getPath());
        }
    }

    private static void say(String message) {
        System.out.println(TAG + message);
    }
}
