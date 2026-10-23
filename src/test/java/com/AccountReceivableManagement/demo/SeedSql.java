package com.AccountReceivableManagement.demo;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal reader for src/main/resources/sql/demo/local_demo_seed.sql, shared by the seed tests. It understands exactly what the
 * seed uses: {@code INSERT [IGNORE] INTO t (cols) VALUES (...),(...);} with string literals, numbers, NULL,
 * {@code UNHEX('..')} and natural-key subselects. It never executes anything.
 */
final class SeedSql {

    static final String RESOURCE = "sql/demo/local_demo_seed.sql";

    record Insert(String table, boolean ignore, List<String> columns, List<Map<String, String>> rows) {
    }

    /** A natural-key subselect such as {@code (SELECT tax_region_id FROM tax_region_master WHERE tax_region_code = 'DEMO-US')}. */
    record NaturalKey(String column, String table, String naturalColumn, String value) {
    }

    private static final Pattern INSERT_HEAD = Pattern.compile("INSERT (IGNORE )?INTO (\\w+) \\(([^)]*)\\) VALUES", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNHEX = Pattern.compile("UNHEX\\('([0-9a-fA-F]{32})'\\)");
    private static final Pattern SUBSELECT = Pattern.compile("\\(SELECT (\\w+) FROM (\\w+) WHERE (\\w+) = '([^']*)'\\)");

    private SeedSql() {
    }

    static String read() throws IOException {
        try (InputStream in = SeedSql.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The script without comment lines. */
    static String withoutCommentLines(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\\R", -1)) {
            if (!line.stripLeading().startsWith("--")) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    /**
     * Statements only: comments (whole-line and trailing "-- ...") removed and every string literal blanked, so keyword scans
     * cannot hit text. A real MySQL client also treats "-- " outside a string as a comment, so this mirrors how it reads the file.
     */
    static String codeOnly(String sql) {
        final char quoteChar = (char) 39;
        final char newline = (char) 10;
        String code = withoutCommentLines(sql);
        StringBuilder out = new StringBuilder(code.length());
        boolean quote = false;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (quote) {
                if (c == quoteChar) {
                    if (i + 1 < code.length() && code.charAt(i + 1) == quoteChar) {
                        i++;
                        out.append("  ");
                    } else {
                        quote = false;
                        out.append(quoteChar);
                    }
                } else {
                    out.append(c == newline ? newline : ' ');
                }
            } else if (c == '-' && code.startsWith("-- ", i)) {
                while (i < code.length() && code.charAt(i) != newline) {
                    i++;
                }
                if (i < code.length()) {
                    out.append(newline);
                }
            } else {
                if (c == quoteChar) {
                    quote = true;
                }
                out.append(c);
            }
        }
        return out.toString();
    }

    static List<Insert> inserts(String sql) {
        String code = withoutCommentLines(sql);
        List<Insert> result = new ArrayList<>();
        Matcher m = INSERT_HEAD.matcher(code);
        while (m.find()) {
            int start = m.end();
            int end = endOfStatement(code, start);
            List<String> columns = new ArrayList<>();
            for (String c : m.group(3).split(",")) {
                columns.add(c.trim());
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (String row : splitRows(code.substring(start, end))) {
                List<String> values = splitTopLevel(row);
                if (values.size() != columns.size()) {
                    throw new IllegalStateException(m.group(2) + ": " + values.size() + " values for " + columns.size() + " columns");
                }
                Map<String, String> map = new LinkedHashMap<>();
                for (int i = 0; i < columns.size(); i++) {
                    map.put(columns.get(i), values.get(i));
                }
                rows.add(map);
            }
            result.add(new Insert(m.group(2), m.group(1) != null, columns, rows));
        }
        return result;
    }

    static List<Insert> insertsOf(List<Insert> all, String table) {
        return all.stream().filter(i -> i.table().equals(table)).toList();
    }

    static List<Map<String, String>> rows(List<Insert> all, String table) {
        List<Map<String, String>> rows = new ArrayList<>();
        insertsOf(all, table).forEach(i -> rows.addAll(i.rows()));
        return rows;
    }

    // ------------------------------------------------------------------------------------------------ token helpers

    static boolean isNull(String token) {
        return "NULL".equals(token);
    }

    static String str(String token) {
        if (isNull(token)) {
            return null;
        }
        if (!(token.startsWith("'") && token.endsWith("'"))) {
            throw new IllegalArgumentException("not a string literal: " + token);
        }
        return token.substring(1, token.length() - 1).replace("''", "'");
    }

    static BigDecimal dec(String token) {
        return isNull(token) ? null : new BigDecimal(token);
    }

    static long num(String token) {
        return Long.parseLong(token);
    }

    static boolean bool(String token) {
        return "1".equals(token);
    }

    static LocalDate date(String token) {
        return isNull(token) ? null : LocalDate.parse(str(token));
    }

    static String uuidHex(String token) {
        Matcher m = UNHEX.matcher(token);
        if (!m.matches()) {
            throw new IllegalArgumentException("not an UNHEX uuid: " + token);
        }
        return m.group(1).toLowerCase();
    }

    static NaturalKey naturalKey(String token) {
        Matcher m = SUBSELECT.matcher(token);
        if (!m.matches()) {
            throw new IllegalArgumentException("not a natural-key subselect: " + token);
        }
        return new NaturalKey(m.group(1), m.group(2), m.group(3), m.group(4));
    }

    // ------------------------------------------------------------------------------------------------ scanning

    private static int endOfStatement(String text, int from) {
        boolean quote = false;
        int depth = 0;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote) {
                if (c == '\'') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                        i++;
                    } else {
                        quote = false;
                    }
                }
            } else if (c == '\'') {
                quote = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ';' && depth == 0) {
                return i;
            }
        }
        throw new IllegalStateException("unterminated INSERT statement");
    }

    private static List<String> splitRows(String text) {
        List<String> rows = new ArrayList<>();
        int depth = 0;
        boolean quote = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote) {
                current.append(c);
                if (c == '\'') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                        current.append('\'');
                        i++;
                    } else {
                        quote = false;
                    }
                }
            } else if (c == '\'') {
                quote = true;
                current.append(c);
            } else if (c == '(') {
                depth++;
                if (depth > 1) {
                    current.append(c);
                }
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    rows.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            } else if (depth >= 1) {
                current.append(c);
            }
        }
        return rows;
    }

    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean quote = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote) {
                current.append(c);
                if (c == '\'') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                        current.append('\'');
                        i++;
                    } else {
                        quote = false;
                    }
                }
            } else if (c == '\'') {
                quote = true;
                current.append(c);
            } else if (c == '(') {
                depth++;
                current.append(c);
            } else if (c == ')') {
                depth--;
                current.append(c);
            } else if (c == ',' && depth == 0) {
                parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.toString().isBlank()) {
            parts.add(current.toString().trim());
        }
        return parts;
    }
}
