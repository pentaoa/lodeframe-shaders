package io.github.pentaoa.lodeframe.shaders.translate;

import io.github.pentaoa.lodeframe.shaders.pack.ShaderStage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacyFullscreenTransformer {
    private static final Pattern VERSION = Pattern.compile("(?m)^[ \\t]*#[ \\t]*version[ \\t]+\\d+[^\\r\\n]*");
    private static final Pattern LEGACY_EXTENSION = Pattern.compile("(?m)^[ \\t]*#[ \\t]*extension[ \\t]+GL_ARB_shader_texture_lod[^\\r\\n]*(?:\\R)?");
    private static final Pattern VARYING = Pattern.compile("\\bvarying\\b");
    private static final Pattern SCALAR_UNIFORM = Pattern.compile(
            "(?m)^[ \\t]*uniform[ \\t]+(float|int|bool|vec[234]|ivec[234]|uvec[234]|mat[234])[ \\t]+([^;]+);[ \\t]*(?://[^\\r\\n]*)?"
    );

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

        UniformExtraction uniforms = extractScalarUniforms(transformed);
        transformed = uniforms.source();

        StringBuilder compatibility = new StringBuilder();
        compatibility.append("\n#ifndef MC_VERSION\n#define MC_VERSION 2602\n#endif\n");
        compatibility.append("#ifndef MC_OS_MAC\n#define MC_OS_MAC\n#endif\n");
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
            compatibility.append("layout(location = 0) out vec4 lodeframeFragColor;\n");
            transformed = transformed.replaceAll("\\bgl_FragColor\\b", "lodeframeFragColor");
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

    private record UniformExtraction(String source, List<String> declarations) {
    }
}
