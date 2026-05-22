package com.llama4j.agent.tools;

import com.llama4j.tools.annotation.Tool;
import com.llama4j.tools.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class EditFileTool {

    private static final Logger LOG = LoggerFactory.getLogger(EditFileTool.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @Tool(name = "edit_file", description = "Edit a file by replacing specific text. The oldText must match exactly one location in the file. Returns error if zero or multiple matches found.")
    public String editFile(
        @ToolParam(description = "Absolute path to the file") String path,
        @ToolParam(description = "Exact text to find and replace (must match exactly one location)") String oldText,
        @ToolParam(description = "New text to replace with") String newText
    ) {
        try {
            Path filePath = Path.of(path);
            if (!Files.exists(filePath)) {
                return "Error: File not found: " + path;
            }
            if (Files.size(filePath) > MAX_FILE_SIZE) {
                return "Error: File too large for editing (" + Files.size(filePath) + " bytes, max " + MAX_FILE_SIZE + ")";
            }

            String content = Files.readString(filePath);
            int firstIdx = content.indexOf(oldText);

            if (firstIdx == -1) {
                return "Error: oldText not found in file. Make sure the text matches exactly.";
            }

            int lastIdx = content.lastIndexOf(oldText);
            if (firstIdx != lastIdx) {
                return "Error: oldText matches multiple locations (" + countOccurrences(content, oldText) + " times). Please provide more context to make it unique.";
            }

            String newContent = content.substring(0, firstIdx) + newText + content.substring(firstIdx + oldText.length());
            Files.writeString(filePath, newContent);

            int lineNum = content.substring(0, firstIdx).split("\n").length;
            return String.format("File edited successfully: %s (at line %d)", path, lineNum);
        } catch (IOException e) {
            LOG.error("Failed to edit file: {}", path, e);
            return "Error editing file: " + e.getMessage();
        }
    }

    private int countOccurrences(String text, String search) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(search, idx)) != -1) {
            count++;
            idx += search.length();
        }
        return count;
    }
}
