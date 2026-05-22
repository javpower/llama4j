package com.llama4j.agent.tools;

import com.llama4j.tools.annotation.Tool;
import com.llama4j.tools.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class WriteFileTool {

    private static final Logger LOG = LoggerFactory.getLogger(WriteFileTool.class);

    @Tool(name = "write_file", description = "Write content to a file. Creates the file if it does not exist, overwrites if it does. Creates parent directories as needed.")
    public String writeFile(
        @ToolParam(description = "Absolute path to the file") String path,
        @ToolParam(description = "Content to write to the file") String content
    ) {
        try {
            Path filePath = Path.of(path);
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(filePath, content);
            long bytes = Files.size(filePath);
            return String.format("File written successfully: %s (%d bytes)", path, bytes);
        } catch (IOException e) {
            LOG.error("Failed to write file: {}", path, e);
            return "Error writing file: " + e.getMessage();
        }
    }
}
