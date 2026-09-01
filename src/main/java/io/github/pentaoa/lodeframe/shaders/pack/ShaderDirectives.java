package io.github.pentaoa.lodeframe.shaders.pack;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ShaderDirectives(
        List<RenderTargets> renderTargetCandidates,
        Map<Integer, String> bufferFormats,
        Map<Integer, Boolean> bufferClears,
        Map<Integer, ClearColor> bufferClearColors,
        Set<Integer> mipmappedBuffers
) {
    public ShaderDirectives {
        renderTargetCandidates = List.copyOf(renderTargetCandidates);
        bufferFormats = Map.copyOf(bufferFormats);
        bufferClears = Map.copyOf(bufferClears);
        bufferClearColors = Map.copyOf(bufferClearColors);
        mipmappedBuffers = Set.copyOf(mipmappedBuffers);
    }

    public static ShaderDirectives empty() {
        return new ShaderDirectives(List.of(), Map.of(), Map.of(), Map.of(), Set.of());
    }

    public enum RenderTargetsKind {
        DRAWBUFFERS,
        RENDERTARGETS
    }

    public record RenderTargets(RenderTargetsKind kind, List<Integer> buffers, int sourceOffset) {
        public RenderTargets {
            buffers = List.copyOf(buffers);
        }
    }

    public record ClearColor(float red, float green, float blue, float alpha) {
    }
}
