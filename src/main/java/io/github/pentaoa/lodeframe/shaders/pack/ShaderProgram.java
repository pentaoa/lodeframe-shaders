package io.github.pentaoa.lodeframe.shaders.pack;

import java.util.Map;
import java.util.Optional;

public record ShaderProgram(
        String dimension,
        String name,
        ShaderProgramType type,
        int index,
        Map<ShaderStage, ShaderEntry> stages,
        ShaderDirectives directives
) {
    public ShaderProgram {
        stages = Map.copyOf(stages);
    }

    public String key() {
        return this.dimension + "/" + this.name;
    }

    public Optional<ShaderEntry> stage(final ShaderStage stage) {
        return Optional.ofNullable(this.stages.get(stage));
    }
}
