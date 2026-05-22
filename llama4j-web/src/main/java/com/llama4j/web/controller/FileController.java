package com.llama4j.web.controller;

import com.llama4j.web.service.FileService;
import com.llama4j.web.service.WorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/file")
public class FileController {

    private final FileService fileService;
    private final WorkspaceService workspaceService;

    public FileController(FileService fileService, WorkspaceService workspaceService) {
        this.fileService = fileService;
        this.workspaceService = workspaceService;
    }

    @GetMapping("/read")
    public ResponseEntity<?> readFile(
        @RequestParam String path,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "0") int limit) {
        try {
            String wsPath = workspaceService.getCurrentWorkspace().path();
            return ResponseEntity.ok(fileService.readFile(wsPath, path, offset, limit));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/write")
    public ResponseEntity<?> writeFile(@RequestBody Map<String, String> body) {
        try {
            String wsPath = workspaceService.getCurrentWorkspace().path();
            fileService.writeFile(wsPath, body.get("path"), body.get("content"));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/edit")
    public ResponseEntity<?> editFile(@RequestBody Map<String, String> body) {
        try {
            String wsPath = workspaceService.getCurrentWorkspace().path();
            return ResponseEntity.ok(fileService.editFile(wsPath,
                body.get("path"), body.get("oldText"), body.get("newText")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/search")
    public ResponseEntity<?> searchFiles(@RequestBody Map<String, String> body) {
        try {
            String wsPath = workspaceService.getCurrentWorkspace().path();
            return ResponseEntity.ok(Map.of("results", fileService.searchFiles(wsPath,
                body.get("pattern"), body.get("glob"), body.get("path"))));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/find")
    public ResponseEntity<?> findFiles(@RequestBody Map<String, String> body) {
        try {
            String wsPath = workspaceService.getCurrentWorkspace().path();
            return ResponseEntity.ok(Map.of("files", fileService.findFiles(wsPath,
                body.get("pattern"), body.get("path"))));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
