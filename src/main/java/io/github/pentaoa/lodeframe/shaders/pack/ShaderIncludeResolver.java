package io.github.pentaoa.lodeframe.shaders.pack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ShaderIncludeResolver {
    private static final Pattern INCLUDE = Pattern.compile("(?m)^[ \\t]*#[ \\t]*include[ \\t]+[\\\"<]([^\\\">]+)[\\\">][ \\t]*(?://.*)?$");

    private final ShaderPack pack;

    ShaderIncludeResolver(final ShaderPack pack) {
        this.pack = pack;
    }

    ResolvedShader resolve(final Path entry) throws IOException, ShaderPackException {
        Set<String> dependencies = new LinkedHashSet<>();
        String source = resolve(entry, new ArrayDeque<>(), dependencies);
        return new ResolvedShader(source, List.copyOf(dependencies));
    }

    private String resolve(
            final Path file,
            final ArrayDeque<Path> stack,
            final Set<String> dependencies
    ) throws IOException, ShaderPackException {
        if (stack.contains(file)) {
            throw new ShaderPackException("Include cycle: " + formatCycle(stack, file));
        }

        stack.addLast(file);
        try {
            String source = pack.read(file);
            Matcher matcher = INCLUDE.matcher(source);
            StringBuilder output = new StringBuilder(source.length());
            int cursor = 0;
            while (matcher.find()) {
                output.append(source, cursor, matcher.start());
                Path include = pack.resolveInclude(file, matcher.group(1));
                dependencies.add(pack.displayPath(include));
                output.append(resolve(include, stack, dependencies));
                cursor = matcher.end();
            }
            output.append(source, cursor, source.length());
            return output.toString();
        } finally {
            stack.removeLast();
        }
    }

    private String formatCycle(final ArrayDeque<Path> stack, final Path repeated) {
        return stack.stream()
                .map(pack::displayPath)
                .reduce((left, right) -> left + " -> " + right)
                .orElse("") + " -> " + pack.displayPath(repeated);
    }
}
