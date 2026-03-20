package com.mazda.control

import android.content.Context
import android.os.RemoteException
import android.util.Log
import androidx.annotation.Keep
import com.mazda.control.IShellExecutor
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * UserService for executing shell commands via Shizuku.
 *
 * Runs as UID 2000 (shell) without root privileges.
 * This replaces the deprecated Shizuku.newProcess() API.
 *
 * Start via: Shizuku.bindUserService(userServiceArgs, serviceConnection)
 */
class ShellExecutorService : IShellExecutor.Stub() {

    companion object {
        private const val TAG = "ShellExecutorService"
    }

    /**
     * Reserved destroy method for Shizuku protocol.
     * Clean up resources - do NOT call System.exit() as it kills the entire app.
     */
    override fun destroy() {
        Log.i(TAG, "destroy() called - cleaning up service")
        // Service will be destroyed by the system, no manual cleanup needed
        // Do NOT call System.exit() - it kills the entire application
    }

    /**
     * Execute a shell command and return the output.
     */
    @Throws(RemoteException::class)
    override fun execute(command: String?): String {
        if (command == null) return ""

        Log.i(TAG, "Executing: $command")

        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

        return try {
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }

            val exitCode = process.waitFor()
            process.destroy()
            Log.i(TAG, "Exit code: $exitCode, output length: ${output.length}")

            output.trim()
        } catch (e: Exception) {
            process.destroy()
            Log.e(TAG, "Execution failed", e)
            throw RemoteException("Command failed: ${e.message}")
        }
    }

    /**
     * Execute a shell command and return full result (exit code, output, error).
     */
    @Throws(RemoteException::class)
    override fun executeFull(command: String?): String {
        if (command == null) return jsonError("null command")

        Log.i(TAG, "executeFull: $command")

        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

        return try {
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }

            val errorOutput = BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                reader.readText()
            }

            val exitCode = process.waitFor()
            process.destroy()

            val result = JSONObject().apply {
                put("exitCode", exitCode)
                put("output", output.trim())
                put("errorOutput", errorOutput.trim())
                put("success", exitCode == 0)
            }.toString()

            Log.i(TAG, "Result: $result")
            result
        } catch (e: Exception) {
            process.destroy()
            Log.e(TAG, "executeFull failed", e)
            jsonError(e.message ?: "Unknown error")
        }
    }

    private fun jsonError(message: String): String {
        return JSONObject().apply {
            put("exitCode", -1)
            put("output", "")
            put("errorOutput", message)
            put("success", false)
        }.toString()
    }
}
