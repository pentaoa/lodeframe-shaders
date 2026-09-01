package io.github.pentaoa.lodeframe.shaders.translate;

import io.github.pentaoa.lodeframe.shaders.pack.ShaderStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LegacyFullscreenTransformerTest {
    @Test
    void transformsLegacyVertexInterfaceIntoVertexIdFullscreenTriangle() {
        String transformed = LegacyFullscreenTransformer.transform(ShaderStage.VERTEX, """
                #version 120
                varying vec2 texCoord;
                void main() {
                    texCoord = gl_MultiTexCoord0.xy;
                    gl_Position = ftransform();
                }
                """);

        assertTrue(transformed.startsWith("#version 330 core"));
        assertTrue(transformed.contains("#define MC_VERSION 12602"));
        assertTrue(transformed.contains("#define MC_GL_RENDERER_APPLE"));
        assertTrue(transformed.contains("#define IS_IRIS"));
        assertTrue(transformed.contains("out vec2 texCoord"));
        assertTrue(transformed.contains("gl_VertexID"));
        assertTrue(transformed.contains("#define gl_MultiTexCoord0"));
        assertTrue(transformed.contains("#define ftransform()"));
    }

    @Test
    void transformsLegacyFragmentOutputsFunctionsAndScalarUniforms() {
        LegacyFullscreenTransformer.TransformedShader result = LegacyFullscreenTransformer.transformDetailed(ShaderStage.FRAGMENT, """
                #version 120
                #extension GL_ARB_shader_texture_lod : enable
                varying vec2 texCoord;
                uniform sampler2D colortex1;
                uniform float viewWidth, viewHeight;
                uniform float aspectRatio, frameTimeCounter;
                void main() {
                    gl_FragColor = texture2DLod(colortex1, texCoord, 0.0);
                }
                """);
        String transformed = result.source();

        assertTrue(transformed.contains("in vec2 texCoord"));
        assertTrue(transformed.contains("layout(location = 0) out vec4 lodeframeFragData0"));
        assertTrue(transformed.contains("layout(std140) uniform LodeframeFragmentUniforms"));
        assertTrue(transformed.contains("float viewWidth;"));
        assertTrue(transformed.contains("float viewHeight;"));
        assertTrue(result.uniforms().contains(new LegacyFullscreenTransformer.UniformField("float", "viewWidth")));
        assertEquals(
                new LegacyFullscreenTransformer.SamplerField("sampler2D", "colortex1"),
                result.samplers().getFirst()
        );
        assertEquals(java.util.Set.of(0), result.fragmentOutputLocations());
        assertTrue(transformed.contains("textureLod(colortex1"));
        assertFalse(transformed.contains("GL_ARB_shader_texture_lod"));
        assertFalse(transformed.contains("gl_FragColor"));
    }

    @Test
    void describesSamplerListsAndArrays() {
        LegacyFullscreenTransformer.TransformedShader result = LegacyFullscreenTransformer.transformDetailed(
                ShaderStage.FRAGMENT,
                """
                        #version 120
                        uniform sampler2D colortex0, colortex1;
                        uniform sampler2DShadow shadowtex[2];
                        void main() { gl_FragColor = texture2D(colortex0, vec2(0.0)); }
                        """
        );

        assertEquals(
                java.util.List.of(
                        new LegacyFullscreenTransformer.SamplerField("sampler2D", "colortex0"),
                        new LegacyFullscreenTransformer.SamplerField("sampler2D", "colortex1"),
                        new LegacyFullscreenTransformer.SamplerField("sampler2DShadow", "shadowtex")
                ),
                result.samplers()
        );
    }

    @Test
    void preservesLegacyShadowComparisonVectorSemantics() {
        String transformed = LegacyFullscreenTransformer.transform(ShaderStage.FRAGMENT, """
                #version 120
                uniform sampler2DShadow shadowtex0;
                void main() { gl_FragColor = vec4(shadow2D(shadowtex0, vec3(0.5)).z); }
                """);

        assertTrue(transformed.contains("vec3((coord).xy, 1.0 - (coord).z)"));
    }

    @Test
    void mapsLegacyGradientAndThreeDimensionalTextureFunctions() {
        String transformed = LegacyFullscreenTransformer.transform(ShaderStage.FRAGMENT, """
                #version 120
                uniform sampler2D normals;
                uniform sampler3D volume;
                void main() {
                    gl_FragColor = texture2DGradARB(normals, vec2(0.5), vec2(1.0), vec2(1.0))
                            + texture3D(volume, vec3(0.5));
                }
                """);

        assertTrue(transformed.contains("textureGrad(normals"));
        assertTrue(transformed.contains("texture(volume"));
    }

    @Test
    void renamesSamplerNamedTextureWithoutRenamingTextureFunction() {
        LegacyFullscreenTransformer.TransformedShader result = LegacyFullscreenTransformer.transformDetailed(
                ShaderStage.FRAGMENT,
                """
                        #version 120
                        uniform sampler2D texture;
                        void main() { gl_FragColor = texture2D(texture, vec2(0.0)); }
                        """
        );

        assertTrue(result.source().contains("uniform sampler2D lodeframeLegacyTexture"));
        assertTrue(result.source().contains("texture(lodeframeLegacyTexture"));
        assertEquals("lodeframeLegacyTexture", result.samplers().getFirst().name());
    }

    @Test
    void mapsLegacyFragmentDataIndicesToExplicitMrtOutputs() {
        String transformed = LegacyFullscreenTransformer.transform(ShaderStage.FRAGMENT, """
                #version 120
                void main() {
                    gl_FragData[0] = vec4(1.0);
                    gl_FragData[3] = vec4(0.5);
                }
                """);

        assertTrue(transformed.contains("layout(location = 0) out vec4 lodeframeFragData0"));
        assertTrue(transformed.contains("layout(location = 3) out vec4 lodeframeFragData3"));
        assertTrue(transformed.contains("lodeframeFragData3 = vec4(0.5)"));
        assertFalse(transformed.contains("gl_FragData"));
    }

    @Test
    void exposesLegacyForwardDepthForMetalReversedZ() {
        String transformed = LegacyFullscreenTransformer.transform(ShaderStage.FRAGMENT, """
                #version 120
                void main() { gl_FragColor = vec4(gl_FragCoord.z); }
                """);

        assertTrue(transformed.contains("vec4((1.0 - gl_FragCoord.z))"));
    }

    @Test
    void mapsLegacyTerrainInputsOntoExtendedSodiumVertices() {
        String transformed = LegacyFullscreenTransformer.transformSodiumTerrainVertexDetailed("""
                #version 120
                attribute vec4 mc_Entity;
                attribute vec4 mc_midTexCoord;
                attribute vec4 at_tangent;
                varying vec2 texCoord;
                void main() {
                    texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
                }
                """).source();

        assertTrue(transformed.contains("layout(std140) uniform u_Globals"));
        assertTrue(transformed.contains("layout(push_constant) uniform PC"));
        assertTrue(transformed.contains("in uvec2 a_Position"));
        assertTrue(transformed.contains("in vec4 a_Normal"));
        assertTrue(transformed.contains("in vec4 a_Tangent"));
        assertTrue(transformed.contains("#define mc_Entity lodeframeTerrainEntity()"));
        assertTrue(transformed.contains("#define mc_midTexCoord vec4(a_MidTexCoord"));
        assertTrue(transformed.contains("#define at_tangent lodeframeTerrainTangent()"));
        assertTrue(transformed.contains("a_LightAndData.xy) / 256.0"));
        assertFalse(transformed.contains("attribute vec4"));
    }

    @Test
    void mapsLegacySkyVertexOntoMinecraftPositionGeometry() {
        String transformed = LegacyFullscreenTransformer.transformMinecraftPositionVertexDetailed("""
                #version 120
                attribute vec4 unusedPackAttribute;
                void main() {
                    gl_Position = ftransform();
                }
                """).source();

        assertTrue(transformed.contains("layout(std140) uniform DynamicTransforms"));
        assertTrue(transformed.contains("#define MC_RENDER_STAGE_STARS 6"));
        assertTrue(transformed.contains("layout(std140) uniform Projection"));
        assertTrue(transformed.contains("in vec3 Position"));
        assertTrue(transformed.contains("#define ftransform() (lodeframeLegacyProjection(ProjMat) * ModelViewMat"));
        assertTrue(transformed.contains("gl_Position.z = (gl_Position.w - gl_Position.z) * 0.5"));
        assertTrue(transformed.contains("#define unusedPackAttribute vec4(0)"));
        assertFalse(transformed.contains("attribute vec4"));
    }

    @Test
    void mapsLegacyEntityVertexOntoMinecraftEntityGeometry() {
        String transformed = LegacyFullscreenTransformer.transformMinecraftEntityVertexDetailed("""
                #version 120
                attribute vec4 mc_Entity;
                attribute vec4 mc_midTexCoord;
                attribute vec4 at_tangent;
                void main() {
                    gl_Position = ftransform();
                }
                """).source();

        assertTrue(transformed.contains("in vec3 Position"));
        assertTrue(transformed.contains("in vec4 Color"));
        assertTrue(transformed.contains("in ivec2 UV2"));
        assertTrue(transformed.contains("in vec3 Normal"));
        assertTrue(transformed.contains("#define gl_MultiTexCoord1 vec4(vec2(UV2) / 256.0"));
        assertTrue(transformed.contains("#define mc_midTexCoord vec4(UV0"));
        assertTrue(transformed.contains("#define at_tangent lodeframeMinecraftEntityTangent()"));
    }

    @Test
    void mapsLegacyTexturedVertexOntoMinecraftParticleGeometry() {
        String transformed = LegacyFullscreenTransformer.transformMinecraftParticleVertexDetailed("""
                #version 120
                attribute vec4 mc_midTexCoord;
                void main() { gl_Position = ftransform(); }
                """).source();

        assertTrue(transformed.contains("in vec3 Position"));
        assertTrue(transformed.contains("in vec2 UV0"));
        assertTrue(transformed.contains("in vec4 Color"));
        assertTrue(transformed.contains("in ivec2 UV2"));
        assertFalse(transformed.contains("in vec3 Normal"));
        assertTrue(transformed.contains("#define mc_midTexCoord vec4(UV0"));
    }

    @Test
    void rejectsUnsupportedStages() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LegacyFullscreenTransformer.transform(ShaderStage.COMPUTE, "#version 430\n")
        );
    }
}
