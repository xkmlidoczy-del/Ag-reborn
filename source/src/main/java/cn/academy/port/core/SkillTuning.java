package cn.academy.port.core;

import cn.academy.port.highpower.core.MobilityScale;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Packaged effect parameters shared by cast controllers and client descriptions. */
public final class SkillTuning {
    private static final Pattern PARAMETER =
            Pattern.compile("\\{\\{([a-z][a-z0-9_]*)(?::(s|q[1-5]))?\\}\\}");
    private static final BigDecimal TICKS_PER_SECOND = BigDecimal.valueOf(20);
    private static final String RESOURCE = "/academy-skill-tuning.tsv";
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]*");
    private record Row(Map<String, BigDecimal> fields, List<String> displayFields) {}
    private static final Row EMPTY = new Row(Map.of(), List.of());
    private static final Map<String, Row> ROWS = load();

    private SkillTuning() {}

    public static Set<String> skills() {
        return ROWS.keySet();
    }

    /** Empty for a registered skill without numeric effect parameters. */
    public static Map<String, BigDecimal> fields(String skill) {
        return ROWS.getOrDefault(skill, EMPTY).fields();
    }

    public static List<String> displayFields(String skill) {
        return ROWS.getOrDefault(skill, EMPTY).displayFields().stream().filter(k->!BigDecimal.valueOf(Integer.MAX_VALUE).equals(fields(skill).get(k))).toList();
    }

    public static double number(String skill, String key) {
        return required(skill, key).doubleValue();
    }

    public static int integer(String skill, String key) {
        return required(skill, key).intValueExact();
    }

    public static String format(String skill, String description) {
        var fields = fields(skill);
        return PARAMETER.matcher(description).replaceAll(token -> {
            var value = fields.get(token.group(1));
            if (value == null) return Matcher.quoteReplacement(token.group());
            if(value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE))==0)return "∞";
            String scale = token.group(2);
            if ("s".equals(scale)) {
                value = value.divide(TICKS_PER_SECOND);
            } else if (scale != null) {
                int level = scale.charAt(1) - '0';
                value = value.multiply(BigDecimal.valueOf(MobilityScale.reach(BigDecimal.valueOf(level))));
            }
            return Matcher.quoteReplacement(value.stripTrailingZeros().toPlainString());
        });
    }

    private static BigDecimal required(String skill, String key) {
        BigDecimal value = fields(skill).get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing skill tuning: " + skill + "." + key);
        }
        return value;
    }

    private static Map<String, Row> load() {
        var stream = SkillTuning.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Missing packaged resource " + RESOURCE);
        }
        Map<String, Row> rows = new LinkedHashMap<>();
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            int lineNumber = 0;
            for (String line; (line = reader.readLine()) != null; ) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cells = line.split("\\t", -1);
                String skill = cells[0];
                if (!IDENTIFIER.matcher(skill).matches() || rows.containsKey(skill)) {
                    throw malformed(lineNumber, "invalid or duplicate skill " + skill);
                }
                Map<String, BigDecimal> fields = new LinkedHashMap<>();
                List<String> displayFields = List.of();
                for (int i = 1; i < cells.length; i++) {
                    if (i == 1 && cells[i].startsWith("@display=")) {
                        displayFields = List.of(cells[i].substring("@display=".length()).split(","));
                        continue;
                    }
                    int separator = cells[i].indexOf('=');
                    if (separator < 1) throw malformed(lineNumber, "expected key=value");
                    String key = cells[i].substring(0, separator);
                    if (!IDENTIFIER.matcher(key).matches() || fields.containsKey(key)) {
                        throw malformed(lineNumber, "invalid or duplicate field " + key);
                    }
                    BigDecimal value;
                    try {
                        value = new BigDecimal(cells[i].substring(separator + 1));
                    } catch (NumberFormatException ex) {
                        throw malformed(lineNumber, "invalid number for " + key);
                    }
                    double numeric = value.doubleValue();
                    if (!Double.isFinite(numeric) || (numeric == 0 && value.signum() != 0)) {
                        throw malformed(lineNumber, "number outside supported range for " + key);
                    }
                    fields.put(key, value);
                }
                for (String field : displayFields) {
                    if (!fields.containsKey(field)
                            && !(fields.containsKey(field + "_novice") && fields.containsKey(field + "_mastered"))) {
                        throw malformed(lineNumber, "unknown display field " + field);
                    }
                }
                rows.put(skill, new Row(Collections.unmodifiableMap(fields), displayFields));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + RESOURCE, ex);
        }
        if (rows.isEmpty()) throw new IllegalStateException("Empty " + RESOURCE);
        return Collections.unmodifiableMap(rows);
    }

    private static IllegalArgumentException malformed(int line, String error) {
        return new IllegalArgumentException(RESOURCE + ":" + line + ": " + error);
    }
}
