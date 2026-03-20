package com.mazda.control;

/**
 * AIDL interface for shell command execution via UserService.
 * UserService runs as UID 2000 (shell) without root.
 */
interface IShellExecutor {

    /**
     * Execute a shell command and return the output.
     * @param command Shell command to execute
     * @return Command output (stdout)
     */
    String execute(String command) = 2;

    /**
     * Execute a shell command and return exit code, output, and error output.
     * @param command Shell command to execute
     * @return JSON string with exitCode, output, errorOutput
     */
    String executeFull(String command) = 3;

    /**
     * Reserved destroy method (Shizuku protocol)
     */
    void destroy() = 16777114;
}
