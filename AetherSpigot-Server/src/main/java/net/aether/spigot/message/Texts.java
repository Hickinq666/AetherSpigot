package net.aether.spigot.message;

import java.util.Map;

public final class Texts {

    private Texts() {
    }

    public static String apply(String template, Map<String, String> values) {
        if (template == null) {
            return "";
        }
        String out = template;
        if (values == null) {
            return out;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String replacement = entry.getValue() == null ? "" : entry.getValue();
            out = out.replace("%" + entry.getKey() + "%", replacement);
        }
        return out;
    }

    public static int compareVersions(String left, String right) {
        String[] a = (left == null ? "0" : left).split("[^0-9]+");
        String[] b = (right == null ? "0" : right).split("[^0-9]+");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length && !a[i].isEmpty() ? parse(a[i]) : 0;
            int bv = i < b.length && !b[i].isEmpty() ? parse(b[i]) : 0;
            if (av != bv) {
                return av < bv ? -1 : 1;
            }
        }
        return 0;
    }

    private static int parse(String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
