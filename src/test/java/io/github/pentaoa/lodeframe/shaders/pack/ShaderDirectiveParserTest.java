package io.github.pentaoa.lodeframe.shaders.pack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ShaderDirectiveParserTest {
    @Test
    void parsesLegacyAndModernRenderTargetMappingsInSourceOrder() {
        ShaderDirectives directives = ShaderDirectiveParser.parse("""
                #version 330
                /* DRAWBUFFERS:0178 */
                /* RENDERTARGETS: 0, 3, 7, 13 */
                """);

        assertEquals(2, directives.renderTargetCandidates().size());
        assertEquals(ShaderDirectives.RenderTargetsKind.DRAWBUFFERS, directives.renderTargetCandidates().get(0).kind());
        assertEquals(List.of(0, 1, 7, 8), directives.renderTargetCandidates().get(0).buffers());
        assertEquals(ShaderDirectives.RenderTargetsKind.RENDERTARGETS, directives.renderTargetCandidates().get(1).kind());
        assertEquals(List.of(0, 3, 7, 13), directives.renderTargetCandidates().get(1).buffers());
    }

    @Test
    void parsesBufferFormatsClearStateColorsMipmapsAndLegacyAliases() {
        ShaderDirectives directives = ShaderDirectiveParser.parse("""
                /*
                const int colortex0Format = R11F_G11F_B10F;
                const int gaux2Format = RGB10_A2;
                */
                const bool colortex5Clear = false;
                const vec4 gnormalClearColor = vec4(0., 0.25, -1.0f, 1.0);
                const bool colortex2MipmapEnabled = true;
                // const int gdepthFormat = RGBA32F;
                """);

        assertEquals(Map.of(0, "R11F_G11F_B10F", 5, "RGB10_A2"), directives.bufferFormats());
        assertEquals(Map.of(5, false), directives.bufferClears());
        assertEquals(new ShaderDirectives.ClearColor(0.0F, 0.25F, -1.0F, 1.0F), directives.bufferClearColors().get(2));
        assertEquals(Set.of(2), directives.mipmappedBuffers());
        assertFalse(directives.bufferFormats().containsKey(1));
    }
}
