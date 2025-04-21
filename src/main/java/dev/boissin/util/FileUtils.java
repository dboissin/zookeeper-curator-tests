package dev.boissin.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.curator.framework.recipes.queue.MultiItem;

import dev.boissin.model.FileItem;
import dev.boissin.model.FileItems;

public final class FileUtils {

    private FileUtils() {}

    public static List<String> listXmlFiles(String directoryPath) throws IOException {
        try (Stream<Path> paths = Files.walk(Path.of(directoryPath))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("(?i).*\\.xml$"))
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .toList();
        }
    }

    public static List<FileItem> generateFileItems(String directoryPath) throws IOException {
        final String importId = UUID.randomUUID().toString();
        final String fileType = "test";
        return listXmlFiles(directoryPath).stream()
            .map(filePath -> new FileItem(importId, fileType, filePath))
            .collect(Collectors.toList());
    }

    public static MultiItem<FileItem> generateFileItem(String directoryPath) throws IOException {
        return new FileItems(generateFileItems(directoryPath));
    }

}
