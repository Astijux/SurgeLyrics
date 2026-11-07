package dev.surgelyrics.core;

import java.util.*;

/** Tiny dependency-free JSON reader/writer so the core runs on any Minecraft version. */
public final class Json {
    private Json() {}

    public static Object parse(String s) {
        Parser p = new Parser(s);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != s.length()) throw new IllegalArgumentException("Trailing data at " + p.i);
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) { return o instanceof Map ? (Map<String, Object>) o : Map.of(); }
    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) { return o instanceof List ? (List<Object>) o : List.of(); }
    public static String str(Object o, String def) { return o instanceof String s ? s : def; }
    public static double num(Object o, double def) { return o instanceof Number n ? n.doubleValue() : def; }
    public static boolean bool(Object o, boolean def) { return o instanceof Boolean b ? b : def; }

    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        write(sb, v, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int ind) {
        if (v == null) sb.append("null");
        else if (v instanceof String s) quote(sb, s);
        else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) sb.append(v);
        else if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d); else sb.append(d);
        } else if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append("{\n");
            int n = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                pad(sb, ind + 1); quote(sb, String.valueOf(e.getKey())); sb.append(": ");
                write(sb, e.getValue(), ind + 1);
                if (++n < m.size()) sb.append(',');
                sb.append('\n');
            }
            pad(sb, ind); sb.append('}');
        } else if (v instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append(", "); write(sb, l.get(i), ind); }
            sb.append(']');
        } else throw new IllegalArgumentException("Unsupported type " + v.getClass());
    }

    private static void pad(StringBuilder sb, int n) { for (int i = 0; i < n; i++) sb.append("  "); }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        final String s; int i;
        Parser(String s) { this.s = s; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        char peek() { if (i >= s.length()) throw err("Unexpected end"); return s.charAt(i); }
        RuntimeException err(String m) { return new IllegalArgumentException(m + " at " + i); }
        void expect(char c) { if (peek() != c) throw err("Expected '" + c + "'"); i++; }

        Object value() {
            ws();
            char c = peek();
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return number();
        }
        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            expect('{'); ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws(); String k = string(); ws(); expect(':');
                m.put(k, value()); ws();
                if (peek() == ',') { i++; continue; }
                expect('}'); return m;
            }
        }
        List<Object> array() {
            List<Object> l = new ArrayList<>();
            expect('['); ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                l.add(value()); ws();
                if (peek() == ',') { i++; continue; }
                expect(']'); return l;
            }
        }
        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = peek(); i++;
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = peek(); i++;
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'n' -> sb.append('\n'); case 'r' -> sb.append('\r'); case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b'); case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw err("Bad unicode escape");
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4;
                    }
                    default -> throw err("Bad escape");
                }
            }
        }
        Number number() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw err("Unexpected character");
            return Double.parseDouble(s.substring(st, i));
        }
    }
}
