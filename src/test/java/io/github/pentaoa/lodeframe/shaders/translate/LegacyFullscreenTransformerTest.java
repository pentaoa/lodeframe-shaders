package io.github.pentaoa.lodeframe.shaders.translate;

import io.github.pentaoa.lodeframe.shaders.pack.ShaderStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        String transformed = LegacyFullscreenTransformer.transform(ShaderStage.FRAGMENT, """
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

        assertTrue(transformed.contains("in vec2 texCoord"));
        assertTrue(transformed.contains("layout(location = 0) out vec4 lodeframeFragData0"));
        assertTrue(transformed.contains("layout(std140) uniform LodeframeFullscreenUniforms"));
        assertTrue(transformed.contains("float viewWidth, viewHeight;"));
        assertTrue(transformed.contains("textureLod(colortex1"));
        assertFalse(transformed.contains("GL_ARB_shader_texture_lod"));
        assertFalse(transformed.contains("gl_FragColor"));
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
