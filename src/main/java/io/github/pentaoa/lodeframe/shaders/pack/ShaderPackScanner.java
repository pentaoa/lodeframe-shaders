package io.github.pentaoa.lodeframe.shaders.pack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShaderPackScanner {
    private static final Pattern WORLD_DIRECTORY = Pattern.compile("world-?\\d+");
    private static final Pattern GLSL_VERSION = Pattern.compile("(?m)^[ \\t]*#[ \\t]*version[ \\t]+(\\d+)");
    private static final Pattern CUSTOM_UNIFORM = Pattern.compile("(?m)^[ \\t]*(?:uniform|variable)\\.");
    private static final Pattern CUSTOM_IMAGE = Pattern.compile("(?m)^[ \\t]*image\\.");
    private static final Pattern CUSTOM_TEXTURE = Pattern.compile("(?m)^[ \\t]*texture\\.");
    private static final Pattern CONDITIONAL_PROGRAM = Pattern.compile("(?m)^[ \\t]*program\\.");

    public ShaderPackReport scan(final ShaderPack pack) throws IOException {
        List<ShaderEntry> entries = discoverEntries(pack);
        Map<ShaderStage, Integer> stageCounts = new EnumMap<>(ShaderStage.class);
        Set<String> dimensions = new TreeSet<>();
        Set<String> programs = new LinkedHashSet<>();
        for (ShaderEntry entry : entries) {
            stageCounts.merge(entry.stage(), 1, Integer::sum);
            dimensions.add(entry.dimension());
            programs.add(entry.programKey());
        }

        ShaderIncludeResolver resolver = new ShaderIncludeResolver(pack);
        List<ShaderDiagnostic> diagnostics = new ArrayList<>();
        Set<String> glslVersions = new TreeSet<>();
        int resolvedStages = 0;
        int includeEdges = 0;
        boolean drawBuffers = false;
        boolean legacyGlsl = false;

        for (ShaderEntry entry : entries) {
            try {
                ResolvedShader resolved = resolver.resolve(pack.path(entry.path()));
                resolvedStages++;
                includeEdges += resolved.dependencies().size();
                drawBuffers |= resolved.source().contains("DRAWBUFFERS:");

                Matcher versions = GLSL_VERSION.matcher(resolved.source());
                while (versions.find()) {
                    String version = versions.group(1);
                    glslVersions.add(version);
                    legacyGlsl |= Integer.parseInt(version) < 330;
                }
            } catch (ShaderPackException exception) {
                diagnostics.add(new ShaderDiagnostic(
                        ShaderDiagnostic.Severity.ERROR,
                        entry.path(),
                        exception.getMessage()
                ));
            }
        }

        String properties = pack.readOptional("shaders.properties");
        ShaderPackRequirements requirements = new ShaderPackRequirements(
                legacyGlsl,
                drawBuffers,
                stageCounts.getOrDefault(ShaderStage.COMPUTE, 0) > 0,
                stageCounts.getOrDefault(ShaderStage.GEOMETRY, 0) > 0,
                CUSTOM_UNIFORM.matcher(properties).find(),
                CUSTOM_IMAGE.matcher(properties).find(),
                CUSTOM_TEXTURE.matcher(properties).find(),
                CONDITIONAL_PROGRAM.matcher(properties).find()
        );

        return new ShaderPackReport(
                pack.name(),
                pack.source().toString(),
                Collections.unmodifiableSet(new LinkedHashSet<>(dimensions)),
                List.copyOf(entries),
                programs.size(),
                Map.copyOf(stageCounts),
                resolvedStages,
                includeEdges,
                Collections.unmodifiableSet(new LinkedHashSet<>(glslVersions)),
                requirements,
                List.copyOf(diagnostics)
        );
    }

    private List<ShaderEntry> discoverEntries(final ShaderPack pack) throws IOException {
        List<ShaderEntry> entries = new ArrayList<>();
        for (Path file : pack.files()) {
            String filename = file.getFileName().toString();
            ShaderStage.fromFilename(filename).ifPresent(stage -> {
                String relative = pack.displayPath(file);
                Path relativePath = pack.shadersRoot().relativize(file);
                String firstDirectory = relativePath.getNameCount() > 1 ? relativePath.getName(0).toString() : "";
                String dimension = WORLD_DIRECTORY.matcher(firstDirectory).matches() ? firstDirectory : "default";
                String program = filename.substring(0, filename.length() - stage.extension().length());
                entries.add(new ShaderEntry(dimension, program, stage, relative));
            });
        }
        return entries;
    }
}
