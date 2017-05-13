package org.bndtools.templating;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class LinkResource implements Resource {

    private final Path path;

    public LinkResource(Path path) {
        this.path = path;
    }

    @Override
    public ResourceType getType() {
        return ResourceType.Link;
    }

    @Override
    public InputStream getContent() throws IOException {
        Path targetPath = Files.readSymbolicLink(path);
        return new ByteArrayInputStream(targetPath.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getTextEncoding() {
        return StandardCharsets.UTF_8.name();
    }

}
