package io.github.pentaoa.lodeframe.shaders.pack;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ShaderPackReport(
        String name,
        String source,
        Set<String> dimensions,
        List<ShaderEntry> stageEntries,
        List<ShaderProgram> programs,
        int programCount,
        Map<ShaderStage, Integer> stageCounts,
        int resolvedStageCount,
        int includeEdgeCount,
        Set<String> glslVersions,
        ShaderPackRequirements requirements,
        List<ShaderDiagnostic> diagnostics
) {
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == ShaderDiagnostic.Severity.ERROR);
    }
}
