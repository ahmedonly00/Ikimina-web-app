package rw.ikimina.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Static check that Flyway migrations are forward-only and additive (Hard Rule H3,
 * spec 20.1), and that none of them switches off the database's own guards on the
 * append-only ledger and audit log (H5) or row-level security (spec 5).
 *
 * <p>There is no override. A migration that genuinely needs one of these statements
 * is a design discussion with the product owner, not an annotation.
 *
 * <p>Comments and single-quoted string literals are blanked before matching (so
 * explaining a rule in a comment is fine). Dollar-quoted function bodies are
 * checked like any other code.
 */
final class MigrationSafetyCheck {

    record Violation(String file, int line, String rule, String excerpt) {
        @Override
        public String toString() {
            return file + ":" + line + " [" + rule + "] " + excerpt;
        }
    }

    /** Versioned migrations only: no repeatable (R__) or undo (U__) scripts. */
    static final Pattern FILE_NAME = Pattern.compile("V[1-9][0-9]*__[a-z0-9]+(_[a-z0-9]+)*\\.sql");

    static final Map<String, Pattern> BANNED = banned();

    private MigrationSafetyCheck() {
    }

    static List<Violation> checkDirectory(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            List<Violation> violations = new ArrayList<>();
            files.filter(Files::isRegularFile).sorted().forEach(file -> violations.addAll(checkFile(file)));
            return violations;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<Violation> checkFile(Path file) {
        String name = file.getFileName().toString();
        List<Violation> violations = new ArrayList<>();
        if (!FILE_NAME.matcher(name).matches()) {
            violations.add(new Violation(name, 0, "FILE NAME", "expected V<n>__<lower_snake_description>.sql"));
        }
        try {
            violations.addAll(checkSql(name, Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return violations;
    }

    static List<Violation> checkSql(String name, String sql) {
        String code = blankCommentsAndStrings(sql);
        List<Violation> violations = new ArrayList<>();
        for (Map.Entry<String, Pattern> rule : BANNED.entrySet()) {
            Matcher matcher = rule.getValue().matcher(code);
            while (matcher.find()) {
                int line = lineOf(code, matcher.start());
                violations.add(new Violation(name, line, rule.getKey(), sql.lines().skip(line - 1L).findFirst().orElse("").trim()));
            }
        }
        return violations;
    }

    /**
     * Replaces comments and single-quoted literals with spaces, keeping newlines so
     * line numbers still point at the original file.
     */
    static String blankCommentsAndStrings(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (c == '-' && next == '-') {
                while (i < sql.length() && sql.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && next == '*') {
                int end = sql.indexOf("*/", i + 2);
                int stop = end < 0 ? sql.length() : end + 2;
                for (; i < stop; i++) {
                    out.append(sql.charAt(i) == '\n' ? '\n' : ' ');
                }
            } else if (c == '\'') {
                out.append(' ');
                i++;
                while (i < sql.length()) {
                    char s = sql.charAt(i);
                    if (s == '\'' && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        out.append("  ");
                        i += 2;
                    } else if (s == '\'') {
                        out.append(' ');
                        i++;
                        break;
                    } else {
                        out.append(s == '\n' ? '\n' : ' ');
                        i++;
                    }
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static Map<String, Pattern> banned() {
        Map<String, Pattern> rules = new LinkedHashMap<>();
        // Destroys data or structure.
        rules.put("DROP TABLE", rule("\\bDROP\\s+TABLE\\b"));
        rules.put("DROP COLUMN", rule("\\bDROP\\s+COLUMN\\b"));
        rules.put("DROP SCHEMA", rule("\\bDROP\\s+SCHEMA\\b"));
        rules.put("DROP DATABASE", rule("\\bDROP\\s+DATABASE\\b"));
        // A TRUNCATE statement - but not the TRUNCATE event in a trigger definition
        // ("BEFORE UPDATE OR DELETE OR TRUNCATE ON t"), which is how tables are protected.
        rules.put("TRUNCATE", rule("\\bTRUNCATE\\b(?!(\\s+OR\\s+\\w+)*\\s+ON\\b)"));
        rules.put("DELETE FROM", rule("\\bDELETE\\s+FROM\\b"));
        // Breaks running code that still uses the old name or type (expand/contract instead).
        rules.put("RENAME", rule("\\bRENAME\\b"));
        rules.put("ALTER COLUMN TYPE", rule("\\bALTER\\s+(COLUMN\\s+)?(\"[^\"]+\"|\\w+)\\s+(SET\\s+DATA\\s+)?TYPE\\b"));
        // Switches off the database's own guards on append-only tables (H5) and tenancy (spec 5.3).
        rules.put("DROP TRIGGER", rule("\\bDROP\\s+TRIGGER\\b"));
        rules.put("DROP FUNCTION", rule("\\bDROP\\s+FUNCTION\\b"));
        rules.put("DISABLE TRIGGER", rule("\\bDISABLE\\s+TRIGGER\\b"));
        rules.put("DROP POLICY", rule("\\bDROP\\s+POLICY\\b"));
        rules.put("DISABLE ROW LEVEL SECURITY", rule("\\bDISABLE\\s+ROW\\s+LEVEL\\s+SECURITY\\b"));
        rules.put("NO FORCE ROW LEVEL SECURITY", rule("\\bNO\\s+FORCE\\s+ROW\\s+LEVEL\\s+SECURITY\\b"));
        // \b also keeps NOBYPASSRLS (the safe form) from matching.
        rules.put("BYPASSRLS", rule("\\bBYPASSRLS\\b"));
        return Collections.unmodifiableMap(rules);
    }

    private static Pattern rule(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }
}
