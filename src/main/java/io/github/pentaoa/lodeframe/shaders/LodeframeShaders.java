package io.github.pentaoa.lodeframe.shaders;

import io.github.pentaoa.lodeframe.shaders.pack.ShaderDiagnostic;
import io.github.pentaoa.lodeframe.shaders.pack.ShaderPack;
import io.github.pentaoa.lodeframe.shaders.pack.ShaderPackReport;
import io.github.pentaoa.lodeframe.shaders.pack.ShaderPackScanner;
import io.github.pentaoa.lodeframe.shaders.pack.ShaderStage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

public final class LodeframeShaders {
    private LodeframeShaders() {
    }

    public static void main(final String[] args) {
        if (args.length == 0 || args.length > 2 || (args.length == 2 && !args[0].equals("inspect"))) {
            usage();
            System.exit(2);
        }

        Path source = Path.of(args.length == 1 ? args[0] : args[1]);
        try (ShaderPack pack = ShaderPack.open(source)) {
            ShaderPackReport report = new ShaderPackScanner().scan(pack);
            printReport(report);
            if (report.hasErrors()) {
                System.exit(1);
            }
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("Unable to inspect shader pack: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void printReport(final ShaderPackReport report) {
        System.out.println("Shader pack: " + report.name());
        System.out.println("Source: " + report.source());
        System.out.println("Dimensions: " + String.join(", ", report.dimensions()));
        System.out.println("Programs: " + report.programCount());
        System.out.println("Stages: " + Arrays.stream(ShaderStage.values())
                .filter(stage -> report.stageCounts().getOrDefault(stage, 0) > 0)
                .map(stage -> stage.displayName() + "=" + report.stageCounts().get(stage))
                .reduce((left, right) -> left + ", " + right)
                .orElse("none"));
        System.out.println("Resolved stage entries: " + report.resolvedStageCount() + "/" + report.stageEntries().size());
        System.out.println("Resolved include edges: " + report.includeEdgeCount());
        System.out.println("GLSL versions: " + String.join(", ", report.glslVersions()));

        System.out.println("Required format features:");
        report.requirements().descriptions().forEach(description -> System.out.println("  - " + description));

        if (report.diagnostics().isEmpty()) {
            System.out.println("Diagnostics: none");
        } else {
            System.out.println("Diagnostics:");
            for (ShaderDiagnostic diagnostic : report.diagnostics()) {
                System.out.printf("  %s %s: %s%n", diagnostic.severity(), diagnostic.path(), diagnostic.message());
            }
        }
    }

    private static void usage() {
        System.err.println("Usage: lodeframe-shaders [inspect] <shader-pack.zip|directory>");
    }
}
