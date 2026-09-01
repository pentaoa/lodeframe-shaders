package io.github.pentaoa.lodeframe.shaders.pack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShaderDirectiveParser {
    private static final String BUFFER_NAME = "(colortex\\d+|gcolor|gdepth|gnormal|composite|gaux[1-4])";
    private static final Pattern RENDER_TARGETS = Pattern.compile(
            "/\\*\\s*(DRAWBUFFERS|RENDERTARGETS)\\s*:\\s*([^*]*?)\\*/",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BUFFER_FORMAT = Pattern.compile(
            "(?m)^[ \\t]*const[ \\t]+int[ \\t]+" + BUFFER_NAME + "Format[ \\t]*=[ \\t]*([A-Za-z0-9_]+)[ \\t]*;"
    );
    private static final Pattern BUFFER_CLEAR = Pattern.compile(
            "(?m)^[ \\t]*const[ \\t]+bool[ \\t]+" + BUFFER_NAME + "Clear[ \\t]*=[ \\t]*(true|false)[ \\t]*;"
    );
    private static final Pattern BUFFER_CLEAR_COLOR = Pattern.compile(
            "(?m)^[ \\t]*const[ \\t]+vec4[ \\t]+" + BUFFER_NAME
                    + "ClearColor[ \\t]*=[ \\t]*vec4[ \\t]*\\(([^)]*)\\)[ \\t]*;"
    );
    private static final Pattern BUFFER_MIPMAP = Pattern.compile(
            "(?m)^[ \\t]*const[ \\t]+bool[ \\t]+(colortex\\d+)MipmapEnabled[ \\t]*=[ \\t]*true[ \\t]*;"
    );
    private static final String RENDER_TARGET_MARKER_PREFIX = "lodeframeRenderTargetsMarker";
    private static final Pattern RENDER_TARGET_MARKER = Pattern.compile(
            "(?m)^[ \\t]*const[ \\t]+int[ \\t]+" + RENDER_TARGET_MARKER_PREFIX + "(\\d+)[ \\t]*=[ \\t]*0[ \\t]*;[ \\t]*(?:\\R|$)"
    );

    private ShaderDirectiveParser() {
    }

    public static ShaderDirectives parse(final String source) {
        List<ShaderDirectives.RenderTargets> renderTargets = parseRenderTargetDirectives(source);
        Map<Integer, String> formats = new LinkedHashMap<>();
        Matcher formatMatcher = BUFFER_FORMAT.matcher(source);
        while (formatMatcher.find()) {
            formats.put(bufferIndex(formatMatcher.group(1)), formatMatcher.group(2).toUpperCase(Locale.ROOT));
        }

        Map<Integer, Boolean> clears = new LinkedHashMap<>();
        Matcher clearMatcher = BUFFER_CLEAR.matcher(source);
        while (clearMatcher.find()) {
            clears.put(bufferIndex(clearMatcher.group(1)), Boolean.parseBoolean(clearMatcher.group(2)));
        }

        Map<Integer, ShaderDirectives.ClearColor> clearColors = new LinkedHashMap<>();
        Matcher clearColorMatcher = BUFFER_CLEAR_COLOR.matcher(source);
        while (clearColorMatcher.find()) {
            clearColors.put(bufferIndex(clearColorMatcher.group(1)), parseClearColor(clearColorMatcher.group(2)));
        }

        Set<Integer> mipmapped = new LinkedHashSet<>();
        Matcher mipmapMatcher = BUFFER_MIPMAP.matcher(source);
        while (mipmapMatcher.find()) {
            mipmapped.add(bufferIndex(mipmapMatcher.group(1)));
        }
        return new ShaderDirectives(renderTargets, formats, clears, clearColors, mipmapped);
    }

    public static InstrumentedDirectives instrumentRenderTargets(final String source) {
        Matcher matcher = RENDER_TARGETS.matcher(source);
        StringBuilder instrumented = new StringBuilder(source.length());
        List<ShaderDirectives.RenderTargets> candidates = new ArrayList<>();
        while (matcher.find()) {
            ShaderDirectives.RenderTargetsKind kind = ShaderDirectives.RenderTargetsKind.valueOf(
                    matcher.group(1).toUpperCase(Locale.ROOT)
            );
            String value = matcher.group(2).strip();
            List<Integer> buffers = kind == ShaderDirectives.RenderTargetsKind.DRAWBUFFERS
                    ? parseDrawBuffers(value)
                    : parseRenderTargetList(value);
            int index = candidates.size();
            candidates.add(new ShaderDirectives.RenderTargets(kind, buffers, matcher.start()));
            matcher.appendReplacement(
                    instrumented,
                    Matcher.quoteReplacement("const int " + RENDER_TARGET_MARKER_PREFIX + index + " = 0;")
            );
        }
        matcher.appendTail(instrumented);
        return new InstrumentedDirectives(instrumented.toString(), candidates);
    }

    public static int bufferIndex(final String name) {
        return switch (name) {
            case "gcolor" -> 0;
            case "gdepth" -> 1;
            case "gnormal" -> 2;
            case "composite" -> 3;
            case "gaux1" -> 4;
            case "gaux2" -> 5;
            case "gaux3" -> 6;
            case "gaux4" -> 7;
            default -> {
                if (!name.startsWith("colortex")) {
                    throw new IllegalArgumentException("Unknown color buffer name: " + name);
                }
                int index = Integer.parseInt(name.substring("colortex".length()));
                if (index < 0 || index > 31) {
                    throw new IllegalArgumentException("Color buffer index is outside the Iris range: " + index);
                }
                yield index;
            }
        };
    }

    private static List<ShaderDirectives.RenderTargets> parseRenderTargetDirectives(final String source) {
        List<ShaderDirectives.RenderTargets> result = new ArrayList<>();
        Matcher matcher = RENDER_TARGETS.matcher(source);
        while (matcher.find()) {
            ShaderDirectives.RenderTargetsKind kind = ShaderDirectives.RenderTargetsKind.valueOf(
                    matcher.group(1).toUpperCase(Locale.ROOT)
            );
            String value = matcher.group(2).strip();
            List<Integer> buffers = kind == ShaderDirectives.RenderTargetsKind.DRAWBUFFERS
                    ? parseDrawBuffers(value)
                    : parseRenderTargetList(value);
            result.add(new ShaderDirectives.RenderTargets(kind, buffers, matcher.start()));
        }
        return result;
    }

    private static List<Integer> parseDrawBuffers(final String value) {
        String compact = value.replaceAll("\\s+", "");
        if (compact.isEmpty() || !compact.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Invalid DRAWBUFFERS directive: " + value);
        }
        List<Integer> result = new ArrayList<>(compact.length());
        for (int index = 0; index < compact.length(); index++) {
            result.add(compact.charAt(index) - '0');
        }
        return List.copyOf(result);
    }

    private static List<Integer> parseRenderTargetList(final String value) {
        String[] parts = value.split(",", -1);
        List<Integer> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            String target = part.strip();
            if (target.isEmpty()) {
                throw new IllegalArgumentException("Invalid RENDERTARGETS directive: " + value);
            }
            int index = Integer.parseInt(target);
            if (index < 0 || index > 31) {
                throw new IllegalArgumentException("Render target is outside the Iris range: " + index);
            }
            result.add(index);
        }
        return List.copyOf(result);
    }

    private static ShaderDirectives.ClearColor parseClearColor(final String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Buffer clear color must contain four values: " + value);
        }
        return new ShaderDirectives.ClearColor(
                parseFloat(parts[0]),
                parseFloat(parts[1]),
                parseFloat(parts[2]),
                parseFloat(parts[3])
        );
    }

    private static float parseFloat(final String value) {
        String literal = value.strip();
        if (literal.endsWith("f") || literal.endsWith("F")) {
            literal = literal.substring(0, literal.length() - 1);
        }
        return Float.parseFloat(literal);
    }

    public record InstrumentedDirectives(String source, List<ShaderDirectives.RenderTargets> candidates) {
        public InstrumentedDirectives {
            candidates = List.copyOf(candidates);
        }

        public ResolvedDirectives resolve(final String preprocessedSource) {
            Matcher matcher = RENDER_TARGET_MARKER.matcher(preprocessedSource);
            StringBuilder cleaned = new StringBuilder(preprocessedSource.length());
            ShaderDirectives.RenderTargets active = null;
            while (matcher.find()) {
                int index = Integer.parseInt(matcher.group(1));
                if (index < 0 || index >= this.candidates.size()) {
                    throw new IllegalArgumentException("Unknown render-target marker index: " + index);
                }
                // Iris and OptiFine apply the last surviving directive in source order. Packs such as
                // BSL deliberately place a broad default first and a conditional override after it.
                active = this.candidates.get(index);
                matcher.appendReplacement(cleaned, "");
            }
            matcher.appendTail(cleaned);
            return new ResolvedDirectives(cleaned.toString(), Optional.ofNullable(active));
        }
    }

    public record ResolvedDirectives(String source, Optional<ShaderDirectives.RenderTargets> renderTargets) {
    }
}
