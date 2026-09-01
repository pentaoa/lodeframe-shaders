package io.github.pentaoa.lodeframe.shaders.pack;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class ShaderPack implements AutoCloseable {
    private final Path source;
    private final FileSystem archiveFileSystem;
    private final Path shadersRoot;

    private ShaderPack(final Path source, final FileSystem archiveFileSystem, final Path root) throws IOException {
        this.source = source;
        this.archiveFileSystem = archiveFileSystem;
        this.shadersRoot = root.resolve("shaders").normalize();
        if (!Files.isDirectory(this.shadersRoot)) {
            close();
            throw new IllegalArgumentException("Shader pack does not contain a root shaders/ directory: " + source);
        }
    }

    public static ShaderPack open(final Path source) throws IOException {
        Path normalized = source.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            return new ShaderPack(normalized, null, normalized);
        }
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Shader pack does not exist: " + normalized);
        }
        if (!normalized.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("Shader pack must be a directory or .zip file: " + normalized);
        }

        FileSystem archive = FileSystems.newFileSystem(normalized);
        return new ShaderPack(normalized, archive, archive.getPath("/"));
    }

    public String name() {
        String filename = source.getFileName().toString();
        return filename.toLowerCase(Locale.ROOT).endsWith(".zip")
                ? filename.substring(0, filename.length() - 4)
                : filename;
    }

    public Path source() {
        return source;
    }

    Path shadersRoot() {
        return shadersRoot;
    }

    Path path(final String relativePath) {
        return shadersRoot.resolve(relativePath).normalize();
    }

    List<Path> files() throws IOException {
        try (Stream<Path> paths = Files.walk(shadersRoot)) {
            return paths.filter(Files::isRegularFile)
                    .sorted((left, right) -> displayPath(left).compareTo(displayPath(right)))
                    .toList();
        }
    }

    String displayPath(final Path path) {
        return shadersRoot.relativize(path).toString().replace('\\', '/');
    }

    String read(final Path path) throws IOException {
        return Files.readString(path);
    }

    String readOptional(final String relativePath) throws IOException {
        Path path = path(relativePath);
        return Files.isRegularFile(path) ? read(path) : "";
    }

    Path resolveInclude(final Path includingFile, final String includePath) throws ShaderPackException {
        Path resolved = includePath.startsWith("/")
                ? shadersRoot.resolve(includePath.substring(1))
                : includingFile.getParent().resolve(includePath);
        resolved = resolved.normalize();
        if (!resolved.startsWith(shadersRoot)) {
            throw new ShaderPackException("Include escapes shaders/ root: " + includePath);
        }
        if (!Files.isRegularFile(resolved)) {
            throw new ShaderPackException("Missing include " + includePath);
        }
        return resolved;
    }

    @Override
    public void close() throws IOException {
        if (archiveFileSystem != null && archiveFileSystem.isOpen()) {
            archiveFileSystem.close();
        }
    }
}
