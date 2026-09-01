package io.github.pentaoa.lodeframe.shaders.pack;

import java.util.ArrayList;
import java.util.List;

public record ShaderPackRequirements(
        boolean legacyGlsl,
        boolean drawBuffers,
        boolean computeShaders,
        boolean geometryShaders,
        boolean customUniforms,
        boolean customImages,
        boolean customTextures,
        boolean conditionalPrograms
) {
    public List<String> descriptions() {
        List<String> result = new ArrayList<>();
        if (legacyGlsl) result.add("legacy GLSL compatibility transforms");
        if (drawBuffers) result.add("DRAWBUFFERS attachment routing");
        if (computeShaders) result.add("compute shader stages");
        if (geometryShaders) result.add("geometry shader stages");
        if (customUniforms) result.add("custom uniform expressions");
        if (customImages) result.add("custom image resources");
        if (customTextures) result.add("custom texture resources");
        if (conditionalPrograms) result.add("conditional program scheduling");
        if (result.isEmpty()) result.add("baseline vertex and fragment stages");
        return List.copyOf(result);
    }
}
