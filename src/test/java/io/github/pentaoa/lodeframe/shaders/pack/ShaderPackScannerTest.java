package io.github.pentaoa.lodeframe.shaders.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderPackScannerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void scansDirectoryPackAndResolvesAbsoluteIncludes() throws Exception {
        Path packDirectory = temporaryDirectory.resolve("Example Pack");
        write(packDirectory.resolve("shaders/world0/gbuffers_terrain.vsh"), """
                #version 120
                #define VSH
                #include "/program/gbuffers_terrain.glsl"
                """);
        write(packDirectory.resolve("shaders/world0/gbuffers_terrain.fsh"), """
                #version 120
                #define FSH
                #include "/program/gbuffers_terrain.glsl"
                """);
        write(packDirectory.resolve("shaders/program/gbuffers_terrain.glsl"), """
                #include "/lib/common.glsl"
                /* DRAWBUFFERS:0 */
                void main() {}
                """);
        write(packDirectory.resolve("shaders/lib/common.glsl"), "vec3 sharedColor = vec3(1.0);\n");
        write(packDirectory.resolve("shaders/shaders.properties"), "uniform.float.timeAngle=worldTime / 24000\n");

        try (ShaderPack pack = ShaderPack.open(packDirectory)) {
            ShaderPackReport report = new ShaderPackScanner().scan(pack);

            assertFalse(report.hasErrors());
            assertEquals(1, report.programCount());
            assertEquals(2, report.stageEntries().size());
            assertEquals(2, report.stageCounts().get(ShaderStage.VERTEX) + report.stageCounts().get(ShaderStage.FRAGMENT));
            assertEquals(4, report.includeEdgeCount());
            assertEquals(List.of("120"), List.copyOf(report.glslVersions()));
            assertTrue(report.dimensions().contains("world0"));
            assertTrue(report.requirements().legacyGlsl());
            assertTrue(report.requirements().drawBuffers());
            assertTrue(report.requirements().customUniforms());
        }
    }

    @Test
    void keepsDimensionsAndVersionsInStableOrder() throws Exception {
        Path packDirectory = temporaryDirectory.resolve("Ordered Pack");
        write(packDirectory.resolve("shaders/world1/final.fsh"), "#version 430\nvoid main() {}\n");
        write(packDirectory.resolve("shaders/world-1/final.fsh"), "#version 120\nvoid main() {}\n");
        write(packDirectory.resolve("shaders/world0/final.fsh"), "#version 130\nvoid main() {}\n");

        try (ShaderPack pack = ShaderPack.open(packDirectory)) {
            ShaderPackReport report = new ShaderPackScanner().scan(pack);

            assertEquals(List.of("world-1", "world0", "world1"), List.copyOf(report.dimensions()));
            assertEquals(List.of("120", "130", "430"), List.copyOf(report.glslVersions()));
        }
    }

    @Test
    void scansZipPackWithoutExtractingIt() throws Exception {
        Path zip = temporaryDirectory.resolve("Packed.zip");
        writeZip(zip, Map.of(
                "shaders/world-1/composite.csh", "#version 430\n#include \"/lib/common.glsl\"\nvoid main() {}\n",
                "shaders/lib/common.glsl", "const int value = 1;\n",
                "shaders/shaders.properties", "image.voxelimg=voxeltex red_integer r8ui unsigned_int true false 16 16 16\n"
        ));

        try (ShaderPack pack = ShaderPack.open(zip)) {
            ShaderPackReport report = new ShaderPackScanner().scan(pack);

            assertFalse(report.hasErrors());
            assertEquals("Packed", report.name());
            assertEquals(1, report.programCount());
            assertEquals(1, report.stageCounts().get(ShaderStage.COMPUTE));
            assertEquals(1, report.includeEdgeCount());
            assertTrue(report.requirements().computeShaders());
            assertTrue(report.requirements().customImages());
        }
    }

    @Test
    void reportsMissingIncludeAgainstItsEntryPoint() throws Exception {
        Path packDirectory = temporaryDirectory.resolve("Broken Pack");
        write(packDirectory.resolve("shaders/final.fsh"), "#version 330\n#include \"/lib/missing.glsl\"\n");

        try (ShaderPack pack = ShaderPack.open(packDirectory)) {
            ShaderPackReport report = new ShaderPackScanner().scan(pack);

            assertTrue(report.hasErrors());
            assertEquals(0, report.resolvedStageCount());
            assertEquals("final.fsh", report.diagnostics().getFirst().path());
            assertTrue(report.diagnostics().getFirst().message().contains("Missing include"));
        }
    }

    private static void write(final Path path, final String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }

    private static void writeZip(final Path path, final Map<String, String> entries) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }
}
