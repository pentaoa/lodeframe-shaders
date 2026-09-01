package io.github.pentaoa.lodeframe.shaders.pack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ShaderPackPropertiesTest {
    @Test
    void joinsContinuationsAndSelectsTheCurrentMinecraftVersionBranch() {
        var properties = ShaderPackProperties.parse("""
                #if MC_VERSION >= 11300
                block.10500= minecraft:oak_leaves \\
                    minecraft:birch_leaves
                #elif MC_VERSION >= 10800
                block.10500=minecraft:leaves
                #else
                block.10500=minecraft:legacy_leaves
                #endif
                """, 12602);

        assertEquals(
                List.of("minecraft:oak_leaves", "minecraft:birch_leaves"),
                properties.get("block.10500")
        );
        assertFalse(properties.get("block.10500").contains("minecraft:leaves"));
    }
}
