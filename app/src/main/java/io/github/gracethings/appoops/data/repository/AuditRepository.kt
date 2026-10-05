package io.github.gracethings.appoops.data.repository

import io.github.gracethings.appoops.data.model.AuditLogEntry
import io.github.gracethings.appoops.data.model.OpMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object AuditRepository {
    private val logs = mutableListOf<AuditLogEntry>()
    private val _logsFlow = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    val logsFlow: StateFlow<List<AuditLogEntry>> = _logsFlow.asStateFlow()

    private val _totalInterceptions = MutableStateFlow(0)
    val totalInterceptions: StateFlow<Int> = _totalInterceptions.asStateFlow()

    @Synchronized
    fun record(
        packageName: String,
        appName: String,
        opCode: Int,
        opName: String,
        timestamp: Long = System.currentTimeMillis(),
        count: Int = 1,
        mode: OpMode = OpMode.IGNORE
    ) {
        val entry = AuditLogEntry(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            appName = appName,
            opCode = opCode,
            opName = opName,
            timestamp = timestamp,
            count = count,
            mode = mode
        )
        logs.add(0, entry)
        if (logs.size > 200) {
            logs.removeAt(logs.lastIndex)
        }
        _logsFlow.value = logs.toList()
        _totalInterceptions.value += count
    }

    @Synchronized
    fun clear() {
        logs.clear()
        _logsFlow.value = emptyList()
    }
}

