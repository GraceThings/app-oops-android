package io.github.gracethings.appoops.data.model

data class AuditLogEntry(
    val id: String,
    val packageName: String,
    val appName: String,
    val opCode: Int,
    val opName: String,
    val timestamp: Long,
    val count: Int = 1,
    val mode: OpMode = OpMode.IGNORE
)

data class AppInterceptStat(
    val packageName: String,
    val appName: String,
    val totalCount: Int
)

enum class InterceptTrendRange {
    DAY,
    WEEK,
    MONTH
}

data class TrendBucket(
    val startMillis: Long,
    val endMillis: Long,
    val count: Int
)
