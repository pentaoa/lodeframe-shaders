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

        assertTrue(transformed.contains("#define shadow2D(sampler, coord) vec4(texture(sampler, coord))"));
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
    void rejectsUnsupportedStages() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LegacyFullscreenTransformer.transform(ShaderStage.COMPUTE, "#version 430\n")
        );
    }
}
