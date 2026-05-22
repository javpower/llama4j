package com.llama4j.web.model;

import java.util.List;

public record GitStatus(
    String branch,
    boolean clean,
    List<String> modified,
    List<String> added,
    List<String> deleted,
    List<String> untracked
) {
    public static GitStatus clean(String branch) {
        return new GitStatus(branch, true, List.of(), List.of(), List.of(), List.of());
    }
}
