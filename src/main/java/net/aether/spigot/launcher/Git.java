package net.aether.spigot.launcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Git {

    private final String executable;

    Git(File tools) {
        this.executable = locate(tools);
    }

    boolean available() {
        return executable != null;
    }

    boolean hasCommit(File repo, String commit) {
        if (executable == null) {
            return false;
        }
        try {
            return exec(repo, false, "cat-file", "-e", commit + "^{commit}") == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    void run(File repo, String... args) throws IOException, InterruptedException {
        exec(repo, false, args);
    }

    void require(File repo, String... args) throws Exception {
        int code = exec(repo, true, args);
        if (code != 0) {
            throw new Launcher.BuildException("git " + Arrays.toString(args) + " a échoué (code " + code + ").");
        }
    }

    String output(File repo, String... args) throws Exception {
        Process process = new ProcessBuilder(command(args)).directory(repo).redirectErrorStream(true).start();
        String text = drain(process.getInputStream());
        if (process.waitFor() != 0) {
            throw new Launcher.BuildException("git " + args[0] + " a échoué : " + text.trim());
        }
        return text;
    }

    private int exec(File repo, boolean visible, String... args) throws IOException, InterruptedException {
        if (executable == null) {
            throw new IOException("git est introuvable");
        }
        ProcessBuilder builder = new ProcessBuilder(command(args)).directory(repo);
        if (visible) {
            builder.inheritIO();
            return builder.start().waitFor();
        }
        builder.redirectErrorStream(true);
        Process process = builder.start();
        drain(process.getInputStream());
        return process.waitFor();
    }

    private List<String> command(String... args) {
        List<String> command = new ArrayList<String>();
        command.add(executable);
        command.addAll(Arrays.asList(args));
        return command;
    }

    private static String locate(File tools) {
        if (works("git")) {
            return "git";
        }
        File[] children = tools.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && child.getName().startsWith("PortableGit")) {
                    File exe = new File(child, "bin/git.exe");
                    if (exe.isFile() && works(exe.getPath())) {
                        return exe.getPath();
                    }
                }
            }
        }
        return null;
    }

    private static boolean works(String git) {
        try {
            Process process = new ProcessBuilder(git, "--version").redirectErrorStream(true).start();
            drain(process.getInputStream());
            return process.waitFor() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String drain(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        in.close();
        return out.toString("UTF-8");
    }
}
