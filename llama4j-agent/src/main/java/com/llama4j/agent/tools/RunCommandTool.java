package com.llama4j.agent.tools;

import com.llama4j.tools.annotation.Tool;
import com.llama4j.tools.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class RunCommandTool {

    private static final Logger LOG = LoggerFactory.getLogger(RunCommandTool.class);
    private static final int MAX_OUTPUT = 10000;

    @Tool(name = "run_command", description = "Execute a shell command and return its output. Use with caution for destructive operations.")
    public String runCommand(
        @ToolParam(description = "The shell command to execute") String command,
        @ToolParam(description = "Working directory for the command (absolute path)") String workdir,
        @ToolParam(description = "Timeout in seconds (default 30)", type = "integer", required = false) int timeout
    ) {
        try {
            int timeoutSec = (timeout > 0) ? timeout : 30;
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            String[] cmd = isWindows
                ? new String[]{"cmd", "/c", command}
                : new String[]{"bash", "-c", command};

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new java.io.File(workdir));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return "Error: Command timed out after " + timeoutSec + " seconds";
            }

            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.exitValue();

            if (output.length() > MAX_OUTPUT) {
                output = output.substring(0, MAX_OUTPUT) + "\n... output truncated";
            }

            StringBuilder sb = new StringBuilder();
            if (exitCode != 0) {
                sb.append("Exit code: ").append(exitCode).append("\n");
            }
            sb.append(output.isEmpty() ? "(no output)" : output);
            return sb.toString();
        } catch (IOException e) {
            LOG.error("Failed to run command: {}", command, e);
            return "Error executing command: " + e.getMessage();
        } catch (InterruptedException e) {
            LOG.error("Command interrupted: {}", command, e);
            Thread.currentThread().interrupt();
            return "Command interrupted: " + e.getMessage();
        }
    }
}
