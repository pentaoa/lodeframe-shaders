package io.github.pentaoa.lodeframe.shaders.pack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum ShaderProgramType {
    SETUP(0, false),
    BEGIN(1, true),
    SHADOW(2, false),
    SHADOW_COMPOSITE(3, true),
    PREPARE(4, true),
    GBUFFERS_OPAQUE(5, false),
    DEFERRED(6, true),
    GBUFFERS_TRANSLUCENT(7, false),
    COMPOSITE(8, true),
    FINAL(9, true),
    UNKNOWN(10, false);

    private static final Pattern NUMBERED_PASS = Pattern.compile("(setup|begin|shadowcomp|prepare|deferred|composite)(\\d{0,2})");

    private final int executionOrder;
    private final boolean fullscreen;

    ShaderProgramType(final int executionOrder, final boolean fullscreen) {
        this.executionOrder = executionOrder;
        this.fullscreen = fullscreen;
    }

    public int executionOrder() {
        return this.executionOrder;
    }

    public boolean fullscreen() {
        return this.fullscreen;
    }

    public static Classification classify(final String name) {
        Matcher numbered = NUMBERED_PASS.matcher(name);
        if (numbered.matches()) {
            ShaderProgramType type = switch (numbered.group(1)) {
                case "setup" -> SETUP;
                case "begin" -> BEGIN;
                case "shadowcomp" -> SHADOW_COMPOSITE;
                case "prepare" -> PREPARE;
                case "deferred" -> DEFERRED;
                case "composite" -> COMPOSITE;
                default -> throw new IllegalStateException();
            };
            String suffix = numbered.group(2);
            if (suffix.isEmpty()) {
                return new Classification(type, 0);
            }
            int index = Integer.parseInt(suffix);
            return index >= 1 && index <= 99
                    ? new Classification(type, index)
                    : new Classification(UNKNOWN, 0);
        }
        if (name.equals("final")) {
            return new Classification(FINAL, 0);
        }
        if (name.equals("shadow") || name.startsWith("shadow_")) {
            return new Classification(SHADOW, 0);
        }
        if (name.startsWith("gbuffers_")) {
            ShaderProgramType type = switch (name) {
                case "gbuffers_water", "gbuffers_weather", "gbuffers_hand_water",
                     "gbuffers_particles_translucent", "gbuffers_block_translucent",
                     "gbuffers_entities_translucent" -> GBUFFERS_TRANSLUCENT;
                default -> GBUFFERS_OPAQUE;
            };
            return new Classification(type, 0);
        }
        return new Classification(UNKNOWN, 0);
    }

    public record Classification(ShaderProgramType type, int index) {
    }
}
