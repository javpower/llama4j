package com.llama4j.web.controller;

import com.llama4j.web.service.GitService;
import com.llama4j.web.service.WorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/git")
public class GitController {

    private final GitService gitService;
    private final WorkspaceService workspaceService;

    public GitController(GitService gitService, WorkspaceService workspaceService) {
        this.gitService = gitService;
        this.workspaceService = workspaceService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        try {
            String wsPath = workspaceService.getCurrentWorkspace().path();
            if (!gitService.isGitRepo(wsPath)) {
                return ResponseEntity.ok(Map.of("isGitRepo", false));
            }
            return ResponseEntity.ok(Map.of("isGitRepo", true, "status", gitService.getStatus(wsPath)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/diff")
    public ResponseEntity<?> getDiff(@RequestParam(required = false) String file) {
        try {
            String wsPath = workspaceService.getCurrentWorkspace().path();
            String diff = (file != null) ? gitService.getFileDiff(wsPath, file) : gitService.getDiff(wsPath);
            return ResponseEntity.ok(Map.of("diff", diff));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/log")
    public ResponseEntity<?> getLog(@RequestParam(defaultValue = "20") int limit) {
        try {
            String wsPath = workspaceService.getCurrentWorkspace().path();
            return ResponseEntity.ok(Map.of("commits", gitService.getLog(wsPath, limit)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/commit")
    public ResponseEntity<?> commit(@RequestBody Map<String, Object> body) {
        try {
            String wsPath = workspaceService.getCurrentWorkspace().path();
            String message = (String) body.get("message");
            @SuppressWarnings("unchecked")
            List<String> files = (List<String>) body.get("files");
            String hash = gitService.commit(wsPath, message, files);
            return ResponseEntity.ok(Map.of("hash", hash));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/branches")
    public ResponseEntity<?> getBranches() {
        try {
            String wsPath = workspaceService.getCurrentWorkspace().path();
            return ResponseEntity.ok(Map.of(
                "current", gitService.getCurrentBranch(wsPath),
                "branches", gitService.getBranches(wsPath)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
