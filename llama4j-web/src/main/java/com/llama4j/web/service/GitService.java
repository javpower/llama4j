package com.llama4j.web.service;

import com.llama4j.web.model.GitStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GitService {

    private static final Logger LOG = LoggerFactory.getLogger(GitService.class);

    public boolean isGitRepo(String workspacePath) {
        return Path.of(workspacePath, ".git").toFile().exists();
    }

    public GitStatus getStatus(String workspacePath) {
        try (Repository repo = openRepo(workspacePath);
             Git git = new Git(repo)) {

            String branch = repo.getBranch();
            IndexDiff diff = new IndexDiff(repo, repo.resolve("HEAD"), new FileTreeIterator(repo));
            diff.diff();

            List<String> modified = new ArrayList<>(diff.getModified());
            List<String> added = new ArrayList<>(diff.getAdded());
            List<String> deleted = new ArrayList<>(diff.getRemoved());
            List<String> untracked = new ArrayList<>(diff.getUntracked());

            boolean clean = modified.isEmpty() && added.isEmpty() && deleted.isEmpty() && untracked.isEmpty();
            return new GitStatus(branch, clean, modified, added, deleted, untracked);
        } catch (IOException e) {
            LOG.error("Failed to get git status", e);
            return GitStatus.clean("unknown");
        }
    }

    public String getDiff(String workspacePath) {
        try (Repository repo = openRepo(workspacePath);
             Git git = new Git(repo)) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter df = new DiffFormatter(out)) {
                df.setRepository(repo);
                df.setDetectRenames(true);
                List<DiffEntry> entries = df.scan(getHeadTreeIterator(repo), new FileTreeIterator(repo));
                for (DiffEntry entry : entries) {
                    df.format(entry);
                }
            }
            return out.toString();
        } catch (Exception e) {
            LOG.error("Failed to get diff", e);
            return "Error: " + e.getMessage();
        }
    }

    public String getFileDiff(String workspacePath, String filePath) {
        try (Repository repo = openRepo(workspacePath);
             Git git = new Git(repo)) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter df = new DiffFormatter(out)) {
                df.setRepository(repo);
                df.setDetectRenames(true);
                List<DiffEntry> entries = df.scan(getHeadTreeIterator(repo), new FileTreeIterator(repo));
                for (DiffEntry entry : entries) {
                    if (entry.getNewPath().equals(filePath) || entry.getOldPath().equals(filePath)) {
                        df.format(entry);
                    }
                }
            }
            return out.toString();
        } catch (Exception e) {
            LOG.error("Failed to get file diff", e);
            return "Error: " + e.getMessage();
        }
    }

    public List<Map<String, String>> getLog(String workspacePath, int limit) {
        try (Repository repo = openRepo(workspacePath);
             Git git = new Git(repo)) {

            List<Map<String, String>> commits = new ArrayList<>();
            for (RevCommit commit : git.log().setMaxCount(limit).call()) {
                commits.add(Map.of(
                    "hash", commit.getName().substring(0, 8),
                    "message", commit.getShortMessage(),
                    "author", commit.getAuthorIdent().getName(),
                    "date", String.valueOf(commit.getCommitTime())
                ));
            }
            return commits;
        } catch (GitAPIException | IOException e) {
            LOG.error("Failed to get git log", e);
            return List.of();
        }
    }

    public List<String> getBranches(String workspacePath) {
        try (Repository repo = openRepo(workspacePath);
             Git git = new Git(repo)) {

            List<String> branches = new ArrayList<>();
            for (Ref ref : git.branchList().call()) {
                String name = ref.getName().replace("refs/heads/", "");
                branches.add(name);
            }
            return branches;
        } catch (GitAPIException | IOException e) {
            LOG.error("Failed to get branches", e);
            return List.of();
        }
    }

    public String commit(String workspacePath, String message, List<String> files) {
        try (Repository repo = openRepo(workspacePath);
             Git git = new Git(repo)) {

            if (files != null && !files.isEmpty()) {
                for (String file : files) {
                    git.add().addFilepattern(file).call();
                }
            } else {
                git.add().addFilepattern(".").call();
            }

            RevCommit commit = git.commit().setMessage(message).call();
            return commit.getName().substring(0, 8);
        } catch (GitAPIException | IOException e) {
            LOG.error("Failed to commit", e);
            throw new RuntimeException("Commit failed: " + e.getMessage(), e);
        }
    }

    public String getCurrentBranch(String workspacePath) {
        try (Repository repo = openRepo(workspacePath)) {
            return repo.getBranch();
        } catch (IOException e) {
            return "unknown";
        }
    }

    private Repository openRepo(String workspacePath) throws IOException {
        return new FileRepositoryBuilder()
            .setGitDir(new File(workspacePath, ".git"))
            .build();
    }

    private AbstractTreeIterator getHeadTreeIterator(Repository repo) throws IOException {
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        try (var reader = repo.newObjectReader()) {
            treeParser.reset(reader, repo.resolve("HEAD^{tree}"));
        }
        return treeParser;
    }
}
