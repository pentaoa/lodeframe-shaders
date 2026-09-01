package io.github.pentaoa.lodeframe.shaders.pack;

import java.util.Locale;
import java.util.Optional;

public enum ShaderStage {
    VERTEX(".vsh", "vertex"),
    FRAGMENT(".fsh", "fragment"),
    GEOMETRY(".gsh", "geometry"),
    COMPUTE(".csh", "compute"),
    TESS_CONTROL(".tcs", "tess-control"),
    TESS_EVALUATION(".tes", "tess-evaluation");

    private final String extension;
    private final String displayName;

    ShaderStage(final String extension, final String displayName) {
        this.extension = extension;
        this.displayName = displayName;
    }

    public String extension() {
        return extension;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<ShaderStage> fromFilename(final String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        for (ShaderStage stage : values()) {
            if (lower.endsWith(stage.extension)) {
                return Optional.of(stage);
            }
        }
        return Optional.empty();
    }
}
