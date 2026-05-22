package com.llama4j.web.controller;

import com.llama4j.web.model.FileNode;
import com.llama4j.web.model.WorkspaceInfo;
import com.llama4j.web.service.WorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping("/open")
    public ResponseEntity<?> openWorkspace(@RequestBody Map<String, String> body) {
        try {
            WorkspaceInfo info = workspaceService.openWorkspace(body.get("path"));
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentWorkspace() {
        WorkspaceInfo info = workspaceService.getCurrentWorkspace();
        if (info == null) {
            return ResponseEntity.ok(Map.of("active", false));
        }
        return ResponseEntity.ok(Map.of("active", true, "workspace", info));
    }

    @DeleteMapping("/close")
    public ResponseEntity<?> closeWorkspace() {
        workspaceService.closeWorkspace();
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/files/tree")
    public ResponseEntity<?> getFileTree(@RequestParam(defaultValue = "5") int depth) {
        try {
            FileNode tree = workspaceService.getFileTree(depth);
            return ResponseEntity.ok(tree);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/files/list")
    public ResponseEntity<?> listDirectories(@RequestParam(defaultValue = "") String path) {
        try {
            List<String> dirs = workspaceService.listDirectories(path);
            return ResponseEntity.ok(Map.of("directories", dirs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
