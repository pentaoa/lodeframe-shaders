package io.github.pentaoa.lodeframe.shaders.pack;

public record ShaderEntry(String dimension, String program, ShaderStage stage, String path) {
    public String programKey() {
        return dimension + "/" + program;
    }
}
