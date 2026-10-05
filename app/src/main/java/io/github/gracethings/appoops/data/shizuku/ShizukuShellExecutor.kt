package io.github.gracethings.appoops.data.shizuku

import io.github.gracethings.appoops.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

data class ShellResult(
    val exitCode: Int,
    val output: String,
    val error: String
)

object ShizukuShellExecutor {
    suspend fun execute(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val exitCode = process.waitFor()
            ShellResult(exitCode, output.trim(), error.trim())
        } catch (e: Throwable) {
            AppLogger.e("Shell execution failed for: $cmd", e)
            ShellResult(-1, "", e.message ?: "Unknown error")
        }
    }
}

