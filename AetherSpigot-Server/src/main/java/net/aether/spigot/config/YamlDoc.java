package net.aether.spigot.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Petit YAML (maps, listes, scalaires, commentaires) qui garde les commentaires
 * tant que le fichier n'est pas modifié, puis réécrit seulement la valeur touchée.
 */
public final class YamlDoc {

    public enum Kind {
        MAP, LIST, SCALAR
    }

    public static final class Node {
        public Kind kind = Kind.MAP;
        public String key;
        public int indent = -2;
        public String rendered;
        public String inlineComment = "";
        public final List<String> leading = new ArrayList<String>();
        public final List<Node> children = new ArrayList<Node>();
        public final List<String> footer = new ArrayList<String>();

        public Node child(String key) {
            for (int i = 0; i < children.size(); i++) {
                Node child = children.get(i);
                if (key.equals(child.key)) {
                    return child;
                }
            }
            return null;
        }
    }

    private final Node root = new Node();
    private String original = "";
    private boolean mutated;

    public YamlDoc() {
        root.kind = Kind.MAP;
        root.indent = -2;
    }

    public static YamlDoc parse(String text) {
        YamlDoc doc = new YamlDoc();
        doc.original = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = doc.original.split("\n", -1);
        int[] index = new int[] {0};
        parseChildren(doc.root, lines, index, -1);
        return doc;
    }

    public boolean mutated() {
        return mutated;
    }

    public Node root() {
        return root;
    }

    public Node at(String path) {
        if (path == null || path.isEmpty() || ".".equals(path)) {
            return root;
        }
        String[] parts = path.split("\\.");
        Node current = root;
        for (int i = 0; i < parts.length; i++) {
            if (current == null) {
                return null;
            }
            current = current.child(parts[i]);
        }
        return current;
    }

    public String scalar(String path) {
        Node node = at(path);
        if (node == null || node.kind == Kind.MAP || node.kind == Kind.LIST) {
            return null;
        }
        return unquote(node.rendered);
    }

    public boolean bool(String path, boolean fallback) {
        String raw = scalar(path);
        if (raw == null) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw) || "oui".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw) || "no".equalsIgnoreCase(raw) || "non".equalsIgnoreCase(raw)) {
            return false;
        }
        return fallback;
    }

    public double decimal(String path, double fallback) {
        String raw = scalar(path);
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public int integer(String path, int fallback) {
        String raw = scalar(path);
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        try {
            if (raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
                return (int) Double.parseDouble(raw);
            }
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public String text(String path, String fallback) {
        String raw = scalar(path);
        return raw == null ? fallback : raw;
    }

    public List<String> list(String path) {
        List<String> values = new ArrayList<String>();
        Node node = at(path);
        if (node == null) {
            return values;
        }
        for (int i = 0; i < node.children.size(); i++) {
            Node child = node.children.get(i);
            if (child.kind == Kind.SCALAR) {
                values.add(unquote(child.rendered));
            }
        }
        return values;
    }

    public List<String> childKeys(String path) {
        Node node = at(path);
        List<String> keys = new ArrayList<String>();
        if (node == null) {
            return keys;
        }
        for (int i = 0; i < node.children.size(); i++) {
            Node child = node.children.get(i);
            if (child.key != null) {
                keys.add(child.key);
            }
        }
        return keys;
    }

    public void set(String path, Object value) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("empty path");
        }
        String[] parts = path.split("\\.");
        Node current = root;
        for (int i = 0; i < parts.length; i++) {
            boolean last = i == parts.length - 1;
            Node child = current.child(parts[i]);
            if (child == null) {
                child = new Node();
                child.key = parts[i];
                child.indent = current.indent + 2;
                if (current.kind != Kind.MAP) {
                    current.kind = Kind.MAP;
                    current.rendered = null;
                }
                current.children.add(child);
            }
            if (last) {
                writeValue(child, value);
            } else if (child.kind == Kind.SCALAR) {
                child.kind = Kind.MAP;
                child.rendered = null;
                child.children.clear();
            }
            current = child;
        }
        mutated = true;
    }

    public String inline(String path) {
        Node node = at(path);
        if (node == null || node.inlineComment == null) {
            return "";
        }
        return node.inlineComment;
    }

    /**
     * Ajoute les clés de {@code defaults} absentes de ce document, juste après la clé qui les précède
     * dans {@code defaults}. Les valeurs déjà présentes ne changent pas. Retourne true si une clé a été ajoutée.
     */
    public boolean addMissing(YamlDoc defaults) {
        boolean changed = merge(root, defaults.root);
        if (changed) {
            mutated = true;
        }
        return changed;
    }

    private static boolean merge(Node target, Node source) {
        if (target.kind != Kind.MAP || source.kind != Kind.MAP) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < source.children.size(); i++) {
            Node child = source.children.get(i);
            if (child.key == null) {
                continue;
            }
            Node existing = target.child(child.key);
            if (existing != null) {
                changed |= merge(existing, child);
                continue;
            }
            int at = target.children.size();
            for (int j = i - 1; j >= 0; j--) {
                Node previous = source.children.get(j).key == null ? null : target.child(source.children.get(j).key);
                if (previous != null) {
                    at = target.children.indexOf(previous) + 1;
                    break;
                }
            }
            reindent(child, target.indent + 2 - child.indent);
            target.children.add(at, child);
            changed = true;
        }
        return changed;
    }

    private static void reindent(Node node, int delta) {
        node.indent += delta;
        for (int i = 0; i < node.children.size(); i++) {
            reindent(node.children.get(i), delta);
        }
    }

    public String save() {
        if (!mutated) {
            return original;
        }
        StringBuilder out = new StringBuilder();
        writeChildren(root, out);
        while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
            out.setLength(out.length() - 1);
        }
        out.append('\n');
        return out.toString();
    }

    private static void writeValue(Node node, Object value) {
        node.children.clear();
        node.footer.clear();
        if (value instanceof List) {
            node.kind = Kind.LIST;
            node.rendered = null;
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                Node item = new Node();
                item.kind = Kind.SCALAR;
                item.indent = node.indent + 2;
                item.rendered = renderScalar(String.valueOf(list.get(i)));
                node.children.add(item);
            }
            return;
        }
        node.kind = Kind.SCALAR;
        if (value instanceof Boolean) {
            node.rendered = ((Boolean) value).booleanValue() ? "true" : "false";
        } else if (value instanceof Integer || value instanceof Long) {
            node.rendered = String.valueOf(value);
        } else if (value instanceof Double || value instanceof Float) {
            node.rendered = trimDouble(((Number) value).doubleValue());
        } else if (value == null) {
            node.rendered = "''";
        } else {
            node.rendered = renderScalar(String.valueOf(value));
        }
    }

    static String trimDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        if (Math.abs(value) >= 1.0E-4 && Math.abs(value) < 1.0E7) {
            String text = Double.toString(value);
            if (text.indexOf('E') < 0 && text.indexOf('e') < 0 && text.indexOf('.') >= 0) {
                while (text.endsWith("0")) {
                    text = text.substring(0, text.length() - 1);
                }
                if (text.endsWith(".")) {
                    text = text.substring(0, text.length() - 1);
                }
            }
            return text;
        }
        return Double.toString(value);
    }

    static String renderScalar(String value) {
        if (value == null) {
            return "''";
        }
        if (value.isEmpty()) {
            return "''";
        }
        boolean quote = value.charAt(0) == ' ' || value.charAt(value.length() - 1) == ' '
                || "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                || "null".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)
                || "no".equalsIgnoreCase(value);
        if (!quote) {
            try {
                Double.parseDouble(value);
                quote = false;
            } catch (NumberFormatException ex) {
                for (int i = 0; i < value.length(); i++) {
                    char c = value.charAt(i);
                    if (c == ':' || c == '#' || c == '\'' || c == '"' || c == '{' || c == '}'
                            || c == '[' || c == ']' || c == ',' || c == '&' || c == '*') {
                        quote = true;
                        break;
                    }
                }
            }
        }
        if (!quote) {
            return value;
        }
        return "'" + value.replace("'", "''") + "'";
    }

    public static String unquote(String rendered) {
        if (rendered == null) {
            return null;
        }
        String value = rendered.trim();
        if (value.length() >= 2 && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            String inner = value.substring(1, value.length() - 1);
            return inner.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
        }
        return value;
    }

    private static void parseChildren(Node parent, String[] lines, int[] index, int parentIndent) {
        while (index[0] < lines.length) {
            int cursor = index[0];
            List<String> pending = new ArrayList<String>();
            while (cursor < lines.length && isSkippable(lines[cursor])) {
                pending.add(lines[cursor]);
                cursor++;
            }
            if (cursor >= lines.length) {
                parent.footer.addAll(pending);
                index[0] = cursor;
                return;
            }
            int indent = indentOf(lines[cursor]);
            if (indent <= parentIndent) {
                return;
            }
            index[0] = cursor + 1;
            String content = lines[cursor].substring(indent);
            String[] split = splitInline(content);
            String body = split[0];
            Node child = new Node();
            child.indent = indent;
            child.inlineComment = split[1];
            child.leading.addAll(pending);
            if (body.startsWith("- ") || "-".equals(body)) {
                parent.kind = Kind.LIST;
                child.key = null;
                String item = body.startsWith("- ") ? body.substring(2).trim() : "";
                if (item.isEmpty()) {
                    child.kind = Kind.MAP;
                    parent.children.add(child);
                    parseChildren(child, lines, index, indent);
                } else {
                    child.kind = Kind.SCALAR;
                    child.rendered = item;
                    parent.children.add(child);
                }
                continue;
            }
            int colon = colonOutsideQuotes(body);
            if (colon < 0) {
                child.kind = Kind.SCALAR;
                child.rendered = body;
                parent.children.add(child);
                continue;
            }
            parent.kind = Kind.MAP;
            child.key = body.substring(0, colon).trim();
            String rest = body.substring(colon + 1).trim();
            parent.children.add(child);
            if (rest.isEmpty()) {
                child.kind = Kind.MAP;
                parseChildren(child, lines, index, indent);
                if (child.children.isEmpty() && child.kind != Kind.LIST) {
                    child.kind = Kind.SCALAR;
                    child.rendered = "''";
                }
            } else {
                child.kind = Kind.SCALAR;
                child.rendered = rest;
            }
        }
    }

    private static void writeChildren(Node parent, StringBuilder out) {
        for (int i = 0; i < parent.children.size(); i++) {
            Node child = parent.children.get(i);
            for (int l = 0; l < child.leading.size(); l++) {
                out.append(child.leading.get(l)).append('\n');
            }
            String indent = spaces(Math.max(0, child.indent));
            if (child.key != null) {
                out.append(indent).append(child.key).append(':');
                if (child.kind == Kind.SCALAR) {
                    out.append(' ');
                    out.append(child.rendered == null ? "''" : child.rendered);
                }
            } else {
                out.append(indent).append("-");
                if (child.kind == Kind.SCALAR) {
                    out.append(' ');
                    out.append(child.rendered == null ? "''" : child.rendered);
                }
            }
            if (child.inlineComment != null && !child.inlineComment.isEmpty()) {
                out.append(" # ").append(child.inlineComment);
            }
            out.append('\n');
            if (child.kind == Kind.MAP || child.kind == Kind.LIST) {
                writeChildren(child, out);
            }
        }
        for (int i = 0; i < parent.footer.size(); i++) {
            out.append(parent.footer.get(i)).append('\n');
        }
    }

    private static boolean isSkippable(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.charAt(0) == '#';
    }

    private static int indentOf(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    private static String spaces(int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(' ');
        }
        return builder.toString();
    }

    static String[] splitInline(String content) {
        boolean single = false;
        boolean dbl = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\'' && !dbl) {
                single = !single;
            } else if (c == '"' && !single) {
                dbl = !dbl;
            } else if (c == '#' && !single && !dbl && i > 0 && content.charAt(i - 1) == ' ') {
                return new String[] {content.substring(0, i).trim(), content.substring(i + 1).trim()};
            }
        }
        return new String[] {content.trim(), ""};
    }

    static int colonOutsideQuotes(String body) {
        boolean single = false;
        boolean dbl = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\'' && !dbl) {
                single = !single;
            } else if (c == '"' && !single) {
                dbl = !dbl;
            } else if (c == ':' && !single && !dbl) {
                return i;
            }
        }
        return -1;
    }
}
