package io.github.pentaoa.lodeframe.shaders.pack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShaderPackProperties {
    private static final Pattern VERSION_CONDITION = Pattern.compile(
            "MC_VERSION\\s*(>=|<=|==|!=|>|<)\\s*(\\d+)"
    );

    private ShaderPackProperties() {
    }

    public static Map<String, List<String>> parse(final String source, final int minecraftVersion) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        Deque<Conditional> conditionals = new ArrayDeque<>();
        for (String line : logicalLines(source)) {
            String stripped = line.strip();
            if (stripped.startsWith("#if ")) {
                boolean parentActive = active(conditionals);
                boolean branch = parentActive && evaluate(stripped.substring(4), minecraftVersion);
                conditionals.push(new Conditional(parentActive, branch, branch));
                continue;
            }
            if (stripped.startsWith("#elif ")) {
                Conditional previous = requireConditional(conditionals, "#elif");
                boolean branch = previous.parentActive()
                        && !previous.branchTaken()
                        && evaluate(stripped.substring(6), minecraftVersion);
                conditionals.push(new Conditional(
                        previous.parentActive(),
                        previous.branchTaken() || branch,
                        branch
                ));
                continue;
            }
            if (stripped.equals("#else")) {
                Conditional previous = requireConditional(conditionals, "#else");
                boolean branch = previous.parentActive() && !previous.branchTaken();
                conditionals.push(new Conditional(previous.parentActive(), true, branch));
                continue;
            }
            if (stripped.equals("#endif")) {
                requireConditional(conditionals, "#endif");
                continue;
            }
            if (!active(conditionals) || stripped.isEmpty() || stripped.startsWith("#")) {
                continue;
            }
            int equals = stripped.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = stripped.substring(0, equals).strip();
            String value = stripped.substring(equals + 1).strip();
            result.put(key, value.isEmpty() ? List.of() : List.of(value.split("\\s+")));
        }
        if (!conditionals.isEmpty()) {
            throw new IllegalArgumentException("Unclosed shader-pack properties conditional");
        }
        return Map.copyOf(result);
    }

    private static Conditional requireConditional(
            final Deque<Conditional> conditionals,
            final String directive
    ) {
        if (conditionals.isEmpty()) {
            throw new IllegalArgumentException(directive + " without #if in shader-pack properties");
        }
        return conditionals.pop();
    }

    private static boolean active(final Deque<Conditional> conditionals) {
        return conditionals.isEmpty() || conditionals.peek().active();
    }

    private static boolean evaluate(final String expression, final int minecraftVersion) {
        Matcher matcher = VERSION_CONDITION.matcher(expression.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported shader-pack properties condition: " + expression);
        }
        int comparedVersion = Integer.parseInt(matcher.group(2));
        return switch (matcher.group(1)) {
            case ">=" -> minecraftVersion >= comparedVersion;
            case "<=" -> minecraftVersion <= comparedVersion;
            case "==" -> minecraftVersion == comparedVersion;
            case "!=" -> minecraftVersion != comparedVersion;
            case ">" -> minecraftVersion > comparedVersion;
            case "<" -> minecraftVersion < comparedVersion;
            default -> throw new IllegalStateException();
        };
    }

    private static List<String> logicalLines(final String source) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : source.replace("\r", "").split("\n", -1)) {
            String trailingStripped = line.stripTrailing();
            boolean continued = trailingStripped.endsWith("\\");
            String part = continued
                    ? trailingStripped.substring(0, trailingStripped.length() - 1)
                    : line;
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(part);
            if (!continued) {
                result.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    private record Conditional(boolean parentActive, boolean branchTaken, boolean active) {
    }
}
