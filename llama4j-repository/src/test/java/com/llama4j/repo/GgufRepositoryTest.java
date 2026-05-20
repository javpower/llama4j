package com.llama4j.repo;

import com.llama4j.exception.ModelNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GgufRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void testLocalFileResolution() throws IOException {
        // Create a real GGUF file to resolve
        Path modelFile = tempDir.resolve("test-model.gguf");
        Files.writeString(modelFile, "dummy gguf content");

        GgufRepository repo = new GgufRepository(tempDir);
        Path resolved = repo.resolve(modelFile.toString());

        assertEquals(modelFile.toAbsolutePath(), resolved.toAbsolutePath());
    }

    @Test
    void testLocalFileNotFound() {
        String nonExistent = "/nonexistent/path/model.gguf";
        GgufRepository repo = new GgufRepository(tempDir);

        ModelNotFoundException ex = assertThrows(ModelNotFoundException.class,
            () -> repo.resolve(nonExistent));
        assertEquals(ModelNotFoundException.CODE, "MODEL_NOT_FOUND");
        assertTrue(ex.getModelPath().contains(nonExistent));
    }
}
