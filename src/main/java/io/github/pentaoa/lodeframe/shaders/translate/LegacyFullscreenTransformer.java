package io.github.pentaoa.lodeframe.shaders.translate;

import io.github.pentaoa.lodeframe.shaders.pack.ShaderStage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacyFullscreenTransformer {
    public static final String LEGACY_TEXTURE_SAMPLER = "lodeframeLegacyTexture";
    private static final Pattern VERSION = Pattern.compile("(?m)^[ \\t]*#[ \\t]*version[ \\t]+\\d+[^\\r\\n]*");
    private static final Pattern LEGACY_EXTENSION = Pattern.compile("(?m)^[ \\t]*#[ \\t]*extension[ \\t]+GL_ARB_shader_texture_lod[^\\r\\n]*(?:\\R)?");
    private static final Pattern VARYING = Pattern.compile("\\bvarying\\b");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "(?m)^[ \\t]*attribute[ \\t]+(float|int|uint|bool|vec[234]|ivec[234]|uvec[234])[ \\t]+([^;]+);[ \\t]*(?://[^\\r\\n]*)?"
    );
    private static final Pattern SCALAR_UNIFORM = Pattern.compile(
            "(?m)^[ \\t]*uniform[ \\t]+(float|int|uint|bool|vec[234]|ivec[234]|uvec[234]|mat[234])[ \\t]+([^;]+);[ \\t]*(?://[^\\r\\n]*)?"
    );
    private static final Pattern SAMPLER_UNIFORM = Pattern.compile(
            "(?m)^[ \\t]*uniform[ \\t]+([iu]?sampler[A-Za-z0-9_]*)[ \\t]+([^;]+);[ \\t]*(?://[^\\r\\n]*)?"
    );
    private static final Pattern FRAGMENT_DATA = Pattern.compile("\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*]");
    private static final Pattern SAMPLER_NAMED_TEXTURE = Pattern.compile(
            "(?m)^[ \\t]*uniform[ \\t]+[iu]?sampler[A-Za-z0-9_]*[ \\t]+(?:[^;]*,)?[ \\t]*texture(?:[ \\t]*[,;])"
    );
    private static final Pattern LEGACY_TEXTURE_IDENTIFIER = Pattern.compile("\\btexture\\b(?![ \\t]*\\()");
    private static final Pattern MAIN_FUNCTION = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");

    private LegacyFullscreenTransformer() {
    }

    public static String transform(final ShaderStage stage, final String source) {
        return transformDetailed(stage, source).source();
    }

    public static TransformedShader transformDetailed(final ShaderStage stage, final String source) {
        return transformDetailed(stage, source, VertexEnvironment.FULLSCREEN);
    }

    public static TransformedShader transformSodiumTerrainVertexDetailed(final String source) {
        return transformDetailed(ShaderStage.VERTEX, source, VertexEnvironment.SODIUM_TERRAIN);
    }

    public static TransformedShader transformMinecraftPositionVertexDetailed(final String source) {
        return transformDetailed(ShaderStage.VERTEX, source, VertexEnvironment.MINECRAFT_POSITION);
    }

    public static TransformedShader transformMinecraftEntityVertexDetailed(final String source) {
        return transformDetailed(ShaderStage.VERTEX, source, VertexEnvironment.MINECRAFT_ENTITY);
    }

    public static TransformedShader transformMinecraftParticleVertexDetailed(final String source) {
        return transformDetailed(ShaderStage.VERTEX, source, VertexEnvironment.MINECRAFT_PARTICLE);
    }

    private static TransformedShader transformDetailed(
            final ShaderStage stage,
            final String source,
            final VertexEnvironment vertexEnvironment
    ) {
        if (stage != ShaderStage.VERTEX && stage != ShaderStage.FRAGMENT) {
            throw new IllegalArgumentException("Fullscreen transformation only supports vertex and fragment stages");
        }

        String transformed = replaceVersion(source);
        transformed = LEGACY_EXTENSION.matcher(transformed).replaceAll("");
        if (SAMPLER_NAMED_TEXTURE.matcher(transformed).find()) {
            transformed = LEGACY_TEXTURE_IDENTIFIER.matcher(transformed).replaceAll(LEGACY_TEXTURE_SAMPLER);
        }
        transformed = VARYING.matcher(transformed).replaceAll(stage == ShaderStage.VERTEX ? "out" : "in");
        transformed = transformed.replaceAll("\\btexture2DGradARB\\b", "textureGrad");
        transformed = transformed.replaceAll("\\btexture2DLod\\b", "textureLod");
        transformed = transformed.replaceAll("\\btexture2D\\b", "texture");
        transformed = transformed.replaceAll("\\btexture3D\\b", "texture");
        transformed = transformed.replaceAll("\\btextureCube\\b", "texture");
        if (stage == ShaderStage.FRAGMENT) {
            transformed = transformed.replaceAll(
                    "\\bgl_FragCoord\\s*\\.\\s*z\\b",
                    "(1.0 - gl_FragCoord.z)"
            );
        }

        AttributeExtraction attributes = stage == ShaderStage.VERTEX
                ? extractAttributes(transformed)
                : new AttributeExtraction(transformed, List.of());
        transformed = attributes.source();

        FragmentOutputs fragmentOutputs = stage == ShaderStage.FRAGMENT
                ? replaceFragmentOutputs(transformed)
                : new FragmentOutputs(transformed, Set.of());
        transformed = fragmentOutputs.source();

        UniformExtraction uniforms = extractScalarUniforms(transformed);
        transformed = uniforms.source();
        List<SamplerField> samplers = extractSamplers(transformed);

        StringBuilder compatibility = new StringBuilder();
        compatibility.append("\n#ifndef MC_VERSION\n#define MC_VERSION 12602\n#endif\n");
        compatibility.append("#ifndef MC_OS_MAC\n#define MC_OS_MAC\n#endif\n");
        compatibility.append("#ifndef MC_GL_RENDERER_APPLE\n#define MC_GL_RENDERER_APPLE\n#endif\n");
        compatibility.append("#ifndef IS_IRIS\n#define IS_IRIS\n#endif\n");
        compatibility.append("""
                #ifndef MC_RENDER_STAGE_NONE
                #define MC_RENDER_STAGE_NONE 0
                #define MC_RENDER_STAGE_SKY 1
                #define MC_RENDER_STAGE_SUNSET 2
                #define MC_RENDER_STAGE_CUSTOM_SKY 3
                #define MC_RENDER_STAGE_SUN 4
                #define MC_RENDER_STAGE_MOON 5
                #define MC_RENDER_STAGE_STARS 6
                #define MC_RENDER_STAGE_VOID 7
                #define MC_RENDER_STAGE_TERRAIN_SOLID 8
                #define MC_RENDER_STAGE_TERRAIN_CUTOUT_MIPPED 9
                #define MC_RENDER_STAGE_TERRAIN_CUTOUT 10
                #define MC_RENDER_STAGE_ENTITIES 11
                #define MC_RENDER_STAGE_BLOCK_ENTITIES 12
                #define MC_RENDER_STAGE_DESTROY 13
                #define MC_RENDER_STAGE_OUTLINE 14
                #define MC_RENDER_STAGE_DEBUG 15
                #define MC_RENDER_STAGE_HAND_SOLID 16
                #define MC_RENDER_STAGE_TERRAIN_TRANSLUCENT 17
                #define MC_RENDER_STAGE_TRIPWIRE 18
                #define MC_RENDER_STAGE_PARTICLES 19
                #define MC_RENDER_STAGE_CLOUDS 20
                #define MC_RENDER_STAGE_RAIN_SNOW 21
                #define MC_RENDER_STAGE_WORLD_BORDER 22
                #define MC_RENDER_STAGE_HAND_TRANSLUCENT 23
                #endif
                """);
        if (!uniforms.fields().isEmpty()) {
            compatibility.append("layout(std140) uniform ").append(uniformBlockName(stage)).append(" {\n");
            for (UniformField field : uniforms.fields()) {
                compatibility.append("    ").append(field.type()).append(' ').append(field.name()).append(";\n");
            }
            compatibility.append("};\n");
        }

        if (stage == ShaderStage.VERTEX) {
            if (vertexEnvironment == VertexEnvironment.SODIUM_TERRAIN) {
                appendSodiumTerrainCompatibility(compatibility, attributes.fields());
            } else if (vertexEnvironment == VertexEnvironment.MINECRAFT_POSITION) {
                appendMinecraftPositionCompatibility(compatibility, attributes.fields());
            } else if (vertexEnvironment == VertexEnvironment.MINECRAFT_ENTITY) {
                appendMinecraftEntityCompatibility(compatibility, attributes.fields());
            } else if (vertexEnvironment == VertexEnvironment.MINECRAFT_PARTICLE) {
                appendMinecraftParticleCompatibility(compatibility, attributes.fields());
            } else {
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
            }
            if (vertexEnvironment != VertexEnvironment.FULLSCREEN) {
                transformed = wrapLegacyVertexMain(transformed);
            }
        } else {
            if (transformed.matches("(?s).*\\bshadow2D\\s*\\(.*")) {
                compatibility.append("#define shadow2D(sampler, coord) vec4(texture(sampler, vec3((coord).xy, 1.0 - (coord).z)))\n");
            }
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
        String result = transformed.substring(0, version.end())
                + compatibility
                + transformed.substring(version.end());
        return new TransformedShader(result, uniforms.fields(), samplers, fragmentOutputs.locations());
    }

    private static void appendSodiumTerrainCompatibility(
            final StringBuilder compatibility,
            final List<AttributeField> attributes
    ) {
        compatibility.append("""
                layout(std140) uniform u_Globals {
                    mat4 u_ProjectionMatrix;
                    mat4 u_ModelViewMatrix;
                    vec4 u_FogColor;
                    vec2 u_EnvironmentFog;
                    vec2 u_RenderFog;
                    vec2 u_TexelSize;
                    vec2 u_TexCoordShrink;
                    float u_FadePeriodInv;
                    bool u_UseRGSS;
                };
                #ifdef VULKAN
                layout(push_constant) uniform PC {
                    vec3 u_RegionOffset;
                    int u_CurrentTime;
                    uint u_RegionID;
                };
                #else
                uniform vec3 u_RegionOffset;
                uniform int u_CurrentTime;
                uniform uint u_RegionID;
                #endif
                in uvec2 a_Position;
                in vec4 a_Color;
                in uvec2 a_TexCoord;
                in uvec4 a_LightAndData;
                in vec4 a_Normal;
                in vec2 a_MidTexCoord;
                in vec4 a_Tangent;
                in int a_BlockId;
                const uint LODEFRAME_POSITION_MAX_COORD = 1u << 20u;
                const uint LODEFRAME_TEXTURE_MAX_COORD = 1u << 15u;
                uvec3 lodeframeDeinterleavePosition(uvec2 data) {
                    uvec3 hi = (uvec3(data.x) >> uvec3(0u, 10u, 20u)) & 0x3FFu;
                    uvec3 lo = (uvec3(data.y) >> uvec3(0u, 10u, 20u)) & 0x3FFu;
                    return (hi << 10u) | lo;
                }
                uvec3 lodeframeRelativeChunkCoord(uint drawId) {
                    return uvec3(drawId) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);
                }
                vec3 lodeframeTerrainPosition() {
                    vec3 local = vec3(lodeframeDeinterleavePosition(a_Position))
                            * (32.0 / float(LODEFRAME_POSITION_MAX_COORD)) - 8.0;
                    return local + u_RegionOffset
                            + vec3(lodeframeRelativeChunkCoord(a_LightAndData.w)) * 16.0;
                }
                vec2 lodeframeTerrainTexCoord() {
                    vec2 coord = vec2(a_TexCoord & (LODEFRAME_TEXTURE_MAX_COORD - 1u))
                            / float(LODEFRAME_TEXTURE_MAX_COORD);
                    vec2 bias = mix(vec2(-1.0), vec2(1.0), bvec2(a_TexCoord >> 15u));
                    return coord + bias * u_TexCoordShrink;
                }
                vec4 lodeframeTerrainVertex() {
                    return vec4(lodeframeTerrainPosition(), 1.0);
                }
                vec4 lodeframeTerrainEntity() {
                    return vec4(float(a_BlockId), 0.0, 0.0, 0.0);
                }
                vec4 lodeframeTerrainTangent() {
                    return a_Tangent;
                }
                mat4 lodeframeLegacyProjection(mat4 projection) {
                    mat4 depthRemap = mat4(1.0);
                    depthRemap[2][2] = -2.0;
                    depthRemap[3][2] = 1.0;
                    return depthRemap * projection;
                }
                const mat4 lodeframeTextureMatrix[2] = mat4[2](mat4(1.0), mat4(1.0));
                #define gl_Vertex lodeframeTerrainVertex()
                #define gl_Color a_Color
                #define gl_Normal a_Normal.xyz
                #define gl_NormalMatrix mat3(u_ModelViewMatrix)
                #define gl_ModelViewMatrix u_ModelViewMatrix
                #define gl_ProjectionMatrix lodeframeLegacyProjection(u_ProjectionMatrix)
                #define gl_ModelViewProjectionMatrix (lodeframeLegacyProjection(u_ProjectionMatrix) * u_ModelViewMatrix)
                #define gl_TextureMatrix lodeframeTextureMatrix
                #define gl_MultiTexCoord0 vec4(lodeframeTerrainTexCoord(), 0.0, 1.0)
                #define gl_MultiTexCoord1 vec4(vec2(a_LightAndData.xy) / 256.0, 0.0, 1.0)
                #define ftransform() (lodeframeLegacyProjection(u_ProjectionMatrix) * u_ModelViewMatrix * lodeframeTerrainVertex())
                """);
        for (AttributeField attribute : attributes) {
            String value = switch (attribute.name()) {
                case "mc_Entity" -> "lodeframeTerrainEntity()";
                case "mc_midTexCoord" -> "vec4(a_MidTexCoord, 0.0, 1.0)";
                case "at_tangent" -> "lodeframeTerrainTangent()";
                default -> attribute.type() + "(0)";
            };
            compatibility.append("#define ")
                    .append(attribute.name())
                    .append(' ')
                    .append(value)
                    .append('\n');
        }
    }

    private static void appendMinecraftPositionCompatibility(
            final StringBuilder compatibility,
            final List<AttributeField> attributes
    ) {
        compatibility.append("""
                layout(std140) uniform DynamicTransforms {
                    mat4 ModelViewMat;
                    vec4 ColorModulator;
                    vec3 ModelOffset;
                    mat4 TextureMat;
                };
                layout(std140) uniform Projection {
                    mat4 ProjMat;
                };
                in vec3 Position;
                vec4 lodeframeMinecraftVertex() {
                    return vec4(Position + ModelOffset, 1.0);
                }
                mat4 lodeframeLegacyProjection(mat4 projection) {
                    mat4 depthRemap = mat4(1.0);
                    depthRemap[2][2] = -2.0;
                    depthRemap[3][2] = 1.0;
                    return depthRemap * projection;
                }
                mat4 lodeframeTextureMatrix[2] = mat4[2](TextureMat, mat4(1.0));
                #define gl_Vertex lodeframeMinecraftVertex()
                #define gl_Color ColorModulator
                #define gl_Normal vec3(0.0, 1.0, 0.0)
                #define gl_NormalMatrix mat3(ModelViewMat)
                #define gl_ModelViewMatrix ModelViewMat
                #define gl_ProjectionMatrix lodeframeLegacyProjection(ProjMat)
                #define gl_ModelViewProjectionMatrix (lodeframeLegacyProjection(ProjMat) * ModelViewMat)
                #define gl_TextureMatrix lodeframeTextureMatrix
                #define gl_MultiTexCoord0 vec4(0.0, 0.0, 0.0, 1.0)
                #define gl_MultiTexCoord1 vec4(0.0, 0.0, 0.0, 1.0)
                #define ftransform() (lodeframeLegacyProjection(ProjMat) * ModelViewMat * lodeframeMinecraftVertex())
                """);
        for (AttributeField attribute : attributes) {
            compatibility.append("#define ")
                    .append(attribute.name())
                    .append(' ')
                    .append(attribute.type())
                    .append("(0)\n");
        }
    }

    private static void appendMinecraftEntityCompatibility(
            final StringBuilder compatibility,
            final List<AttributeField> attributes
    ) {
        compatibility.append("""
                layout(std140) uniform DynamicTransforms {
                    mat4 ModelViewMat;
                    vec4 ColorModulator;
                    vec3 ModelOffset;
                    mat4 TextureMat;
                };
                layout(std140) uniform Projection {
                    mat4 ProjMat;
                };
                in vec3 Position;
                in vec4 Color;
                in vec2 UV0;
                in ivec2 UV1;
                in ivec2 UV2;
                in vec3 Normal;
                vec4 lodeframeMinecraftEntityVertex() {
                    return vec4(Position + ModelOffset, 1.0);
                }
                vec4 lodeframeMinecraftEntityTangent() {
                    vec3 axis = abs(Normal.y) < 0.999 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
                    return vec4(normalize(cross(axis, Normal)), 1.0);
                }
                mat4 lodeframeLegacyProjection(mat4 projection) {
                    mat4 depthRemap = mat4(1.0);
                    depthRemap[2][2] = -2.0;
                    depthRemap[3][2] = 1.0;
                    return depthRemap * projection;
                }
                mat4 lodeframeTextureMatrix[2] = mat4[2](TextureMat, mat4(1.0));
                #define gl_Vertex lodeframeMinecraftEntityVertex()
                #define gl_Color (Color * ColorModulator)
                #define gl_Normal Normal
                #define gl_NormalMatrix mat3(ModelViewMat)
                #define gl_ModelViewMatrix ModelViewMat
                #define gl_ProjectionMatrix lodeframeLegacyProjection(ProjMat)
                #define gl_ModelViewProjectionMatrix (lodeframeLegacyProjection(ProjMat) * ModelViewMat)
                #define gl_TextureMatrix lodeframeTextureMatrix
                #define gl_MultiTexCoord0 vec4(UV0, 0.0, 1.0)
                #define gl_MultiTexCoord1 vec4(vec2(UV2) / 256.0, 0.0, 1.0)
                #define ftransform() (lodeframeLegacyProjection(ProjMat) * ModelViewMat * lodeframeMinecraftEntityVertex())
                """);
        for (AttributeField attribute : attributes) {
            String value = switch (attribute.name()) {
                case "mc_midTexCoord" -> "vec4(UV0, 0.0, 1.0)";
                case "at_tangent" -> "lodeframeMinecraftEntityTangent()";
                default -> attribute.type() + "(0)";
            };
            compatibility.append("#define ")
                    .append(attribute.name())
                    .append(' ')
                    .append(value)
                    .append('\n');
        }
    }

    private static void appendMinecraftParticleCompatibility(
            final StringBuilder compatibility,
            final List<AttributeField> attributes
    ) {
        compatibility.append("""
                layout(std140) uniform DynamicTransforms {
                    mat4 ModelViewMat;
                    vec4 ColorModulator;
                    vec3 ModelOffset;
                    mat4 TextureMat;
                };
                layout(std140) uniform Projection {
                    mat4 ProjMat;
                };
                in vec3 Position;
                in vec2 UV0;
                in vec4 Color;
                in ivec2 UV2;
                vec4 lodeframeMinecraftParticleVertex() {
                    return vec4(Position + ModelOffset, 1.0);
                }
                mat4 lodeframeLegacyProjection(mat4 projection) {
                    mat4 depthRemap = mat4(1.0);
                    depthRemap[2][2] = -2.0;
                    depthRemap[3][2] = 1.0;
                    return depthRemap * projection;
                }
                mat4 lodeframeTextureMatrix[2] = mat4[2](TextureMat, mat4(1.0));
                #define gl_Vertex lodeframeMinecraftParticleVertex()
                #define gl_Color (Color * ColorModulator)
                #define gl_Normal vec3(0.0, 1.0, 0.0)
                #define gl_NormalMatrix mat3(ModelViewMat)
                #define gl_ModelViewMatrix ModelViewMat
                #define gl_ProjectionMatrix lodeframeLegacyProjection(ProjMat)
                #define gl_ModelViewProjectionMatrix (lodeframeLegacyProjection(ProjMat) * ModelViewMat)
                #define gl_TextureMatrix lodeframeTextureMatrix
                #define gl_MultiTexCoord0 vec4(UV0, 0.0, 1.0)
                #define gl_MultiTexCoord1 vec4(vec2(UV2) / 256.0, 0.0, 1.0)
                #define ftransform() (lodeframeLegacyProjection(ProjMat) * ModelViewMat * lodeframeMinecraftParticleVertex())
                """);
        for (AttributeField attribute : attributes) {
            String value = attribute.name().equals("mc_midTexCoord")
                    ? "vec4(UV0, 0.0, 1.0)"
                    : attribute.type() + "(0)";
            compatibility.append("#define ")
                    .append(attribute.name())
                    .append(' ')
                    .append(value)
                    .append('\n');
        }
    }

    public static String uniformBlockName(final ShaderStage stage) {
        return switch (stage) {
            case VERTEX -> "LodeframeVertexUniforms";
            case FRAGMENT -> "LodeframeFragmentUniforms";
            default -> throw new IllegalArgumentException("Fullscreen stages only have vertex and fragment uniform blocks");
        };
    }

    private static String wrapLegacyVertexMain(final String source) {
        Matcher matcher = MAIN_FUNCTION.matcher(source);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Vertex shader source has no main function");
        }
        String renamed = matcher.replaceFirst("void lodeframeLegacyMain()");
        return renamed + """

                void main() {
                    lodeframeLegacyMain();
                    gl_Position.z = (gl_Position.w - gl_Position.z) * 0.5;
                }
                """;
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
        List<UniformField> fields = new ArrayList<>();
        int cursor = 0;
        while (matcher.find()) {
            result.append(source, cursor, matcher.start());
            String type = matcher.group(1);
            for (String name : matcher.group(2).split(",")) {
                fields.add(new UniformField(type, name.strip()));
            }
            cursor = matcher.end();
        }
        result.append(source, cursor, source.length());
        return new UniformExtraction(result.toString(), List.copyOf(fields));
    }

    private static AttributeExtraction extractAttributes(final String source) {
        Matcher matcher = ATTRIBUTE.matcher(source);
        StringBuilder result = new StringBuilder(source.length());
        List<AttributeField> fields = new ArrayList<>();
        int cursor = 0;
        while (matcher.find()) {
            result.append(source, cursor, matcher.start());
            String type = matcher.group(1);
            for (String name : matcher.group(2).split(",")) {
                fields.add(new AttributeField(type, name.strip()));
            }
            cursor = matcher.end();
        }
        result.append(source, cursor, source.length());
        return new AttributeExtraction(result.toString(), List.copyOf(fields));
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

    private static List<SamplerField> extractSamplers(final String source) {
        Matcher matcher = SAMPLER_UNIFORM.matcher(source);
        List<SamplerField> samplers = new ArrayList<>();
        while (matcher.find()) {
            String type = matcher.group(1);
            for (String declaration : matcher.group(2).split(",")) {
                String name = declaration.strip();
                int array = name.indexOf('[');
                if (array >= 0) {
                    name = name.substring(0, array).strip();
                }
                samplers.add(new SamplerField(type, name));
            }
        }
        return List.copyOf(samplers);
    }

    public record TransformedShader(
            String source,
            List<UniformField> uniforms,
            List<SamplerField> samplers,
            Set<Integer> fragmentOutputLocations
    ) {
        public TransformedShader {
            uniforms = List.copyOf(uniforms);
            samplers = List.copyOf(samplers);
            fragmentOutputLocations = Set.copyOf(fragmentOutputLocations);
        }
    }

    public record UniformField(String type, String name) {
    }

    public record SamplerField(String type, String name) {
    }

    private record UniformExtraction(String source, List<UniformField> fields) {
    }

    private record AttributeExtraction(String source, List<AttributeField> fields) {
    }

    private record AttributeField(String type, String name) {
    }

    private record FragmentOutputs(String source, Set<Integer> locations) {
    }

    private enum VertexEnvironment {
        FULLSCREEN,
        SODIUM_TERRAIN,
        MINECRAFT_POSITION,
        MINECRAFT_ENTITY,
        MINECRAFT_PARTICLE
    }
}
