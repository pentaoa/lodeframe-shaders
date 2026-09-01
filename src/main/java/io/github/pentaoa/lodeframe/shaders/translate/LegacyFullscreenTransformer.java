package io.github.pentaoa.lodeframe.shaders.translate;

import io.github.pentaoa.lodeframe.shaders.pack.ShaderStage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacyFullscreenTransformer {
    private static final Pattern VERSION = Pattern.compile("(?m)^[ \\t]*#[ \\t]*version[ \\t]+\\d+[^\\r\\n]*");
    private static final Pattern LEGACY_EXTENSION = Pattern.compile("(?m)^[ \\t]*#[ \\t]*extension[ \\t]+GL_ARB_shader_texture_lod[^\\r\\n]*(?:\\R)?");
    private static final Pattern VARYING = Pattern.compile("\\bvarying\\b");
    private static final Pattern SCALAR_UNIFORM = Pattern.compile(
            "(?m)^[ \\t]*uniform[ \\t]+(float|int|bool|vec[234]|ivec[234]|uvec[234]|mat[234])[ \\t]+([^;]+);[ \\t]*(?://[^\\r\\n]*)?"
    );
    private static final Pattern FRAGMENT_DATA = Pattern.compile("\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*]");

    private LegacyFullscreenTransformer() {
    }

    public static String transform(final ShaderStage stage, final String source) {
        if (stage != ShaderStage.VERTEX && stage != ShaderStage.FRAGMENT) {
            throw new IllegalArgumentException("Fullscreen transformation only supports vertex and fragment stages");
        }

        String transformed = replaceVersion(source);
        transformed = LEGACY_EXTENSION.matcher(transformed).replaceAll("");
        transformed = VARYING.matcher(transformed).replaceAll(stage == ShaderStage.VERTEX ? "out" : "in");
        transformed = transformed.replaceAll("\\btexture2DLod\\b", "textureLod");
        transformed = transformed.replaceAll("\\btexture2D\\b", "texture");
        transformed = transformed.replaceAll("\\btextureCube\\b", "texture");

        FragmentOutputs fragmentOutputs = stage == ShaderStage.FRAGMENT
                ? replaceFragmentOutputs(transformed)
                : new FragmentOutputs(transformed, Set.of());
        transformed = fragmentOutputs.source();

        UniformExtraction uniforms = extractScalarUniforms(transformed);
        transformed = uniforms.source();

        StringBuilder compatibility = new StringBuilder();
        compatibility.append("\n#ifndef MC_VERSION\n#define MC_VERSION 12602\n#endif\n");
        compatibility.append("#ifndef MC_OS_MAC\n#define MC_OS_MAC\n#endif\n");
        compatibility.append("#ifndef MC_GL_RENDERER_APPLE\n#define MC_GL_RENDERER_APPLE\n#endif\n");
        compatibility.append("#ifndef IS_IRIS\n#define IS_IRIS\n#endif\n");
        if (!uniforms.declarations().isEmpty()) {
            compatibility.append("layout(std140) uniform LodeframeFullscreenUniforms {\n");
            for (String declaration : uniforms.declarations()) {
                compatibility.append("    ").append(declaration).append(";\n");
            }
            compatibility.append("};\n");
        }

        if (stage == ShaderStage.VERTEX) {
            compatibility.append("""
                    vec2 lodeframeFullscreenUv() {
                        return vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                    }
                    vec4 lodeframeFullscreenPosition() {
                        return vec4(lodeframeFullscreenUv() * 2.0 - 1.0, 0.0, 1.0);
                    }
                    #define gl_MultiTexCoord0 vec4(lodeframeFullscreenUv(), 0.0, 1.0)
                    #define ftransform() lodeframeFullscreenPosition()
                    """);
        } else {
            for (int location : fragmentOutputs.locations()) {
                compatibility.append("layout(location = ")
                        .append(location)
                        .append(") out vec4 lodeframeFragData")
                        .append(location)
                        .append(";\n");
            }
        }

        Matcher version = VERSION.matcher(transformed);
        if (!version.find()) {
            throw new IllegalArgumentException("Shader source has no #version directive");
        }
        return transformed.substring(0, version.end())
                + compatibility
                + transformed.substring(version.end());
    }

    private static String replaceVersion(final String source) {
        Matcher matcher = VERSION.matcher(source);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Shader source has no #version directive");
        }
        return matcher.replaceFirst("#version 330 core");
    }

    private static UniformExtraction extractScalarUniforms(final String source) {
        Matcher matcher = SCALAR_UNIFORM.matcher(source);
        StringBuilder result = new StringBuilder(source.length());
        List<String> declarations = new ArrayList<>();
        int cursor = 0;
        while (matcher.find()) {
            result.append(source, cursor, matcher.start());
            declarations.add(matcher.group(1) + " " + matcher.group(2).strip());
            cursor = matcher.end();
        }
        result.append(source, cursor, source.length());
        return new UniformExtraction(result.toString(), List.copyOf(declarations));
    }

    private static FragmentOutputs replaceFragmentOutputs(final String source) {
        Set<Integer> locations = new LinkedHashSet<>();
        String transformed = source;
        if (transformed.matches("(?s).*\\bgl_FragColor\\b.*")) {
            locations.add(0);
            transformed = transformed.replaceAll("\\bgl_FragColor\\b", "lodeframeFragData0");
        }

        Matcher matcher = FRAGMENT_DATA.matcher(transformed);
        StringBuilder result = new StringBuilder(transformed.length());
        while (matcher.find()) {
            int location = Integer.parseInt(matcher.group(1));
            locations.add(location);
            matcher.appendReplacement(result, "lodeframeFragData" + location);
        }
        matcher.appendTail(result);
        return new FragmentOutputs(result.toString(), Set.copyOf(locations));
    }

    private record UniformExtraction(String source, List<String> declarations) {
    }

    private record FragmentOutputs(String source, Set<Integer> locations) {
    }
}
