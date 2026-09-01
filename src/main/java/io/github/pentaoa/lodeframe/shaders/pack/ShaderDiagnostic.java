package io.github.pentaoa.lodeframe.shaders.pack;

public record ShaderDiagnostic(Severity severity, String path, String message) {
    public enum Severity {
        WARNING,
        ERROR
    }
}
