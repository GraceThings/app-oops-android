package io.github.gracethings.appoops.data.repository

import android.content.Context
import io.github.gracethings.appoops.data.model.AppInterceptStat
import io.github.gracethings.appoops.data.model.AuditLogEntry
import io.github.gracethings.appoops.data.model.InterceptTrendRange
import io.github.gracethings.appoops.data.model.OpMode
import io.github.gracethings.appoops.data.model.TrendBucket
import io.github.gracethings.appoops.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.Executors

private data class HourlyAppBucket(
    val hourStartMillis: Long,
    val packageName: String,
    var appName: String,
    var count: Int
)

object AuditRepository {
    private const val HISTORY_FILE_NAME = "audit_history_v1.json"
    private const val MAX_LOG_ENTRIES = 500
    private const val HOUR_MS = 3_600_000L
    private const val RETENTION_MS = 31L * 24L * HOUR_MS

    private val logs = mutableListOf<AuditLogEntry>()
    private val hourlyBuckets = LinkedHashMap<String, HourlyAppBucket>()
    private val persistExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var initialized = false

    private val _logsFlow = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    val logsFlow: StateFlow<List<AuditLogEntry>> = _logsFlow.asStateFlow()

    private val _totalInterceptions = MutableStateFlow(0)
    val totalInterceptions: StateFlow<Int> = _totalInterceptions.asStateFlow()

    private val _statsRevision = MutableStateFlow(0)
    val statsRevision: StateFlow<Int> = _statsRevision.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        initialized = true
        loadFromDisk(context.applicationContext)
    }

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
        if (logs.size > MAX_LOG_ENTRIES) {
            logs.removeAt(logs.lastIndex)
        }

        val hourStart = (timestamp / HOUR_MS) * HOUR_MS
        val bucketKey = "$hourStart|$packageName"
        val existing = hourlyBuckets[bucketKey]
        if (existing != null) {
            existing.appName = appName
            existing.count += count
        } else {
            hourlyBuckets[bucketKey] = HourlyAppBucket(
                hourStartMillis = hourStart,
                packageName = packageName,
                appName = appName,
                count = count
            )
        }

        pruneExpiredBucketsLocked(timestamp)

        _logsFlow.value = logs.toList()
        _totalInterceptions.value += count
        _statsRevision.value += 1

        schedulePersistLocked()
    }

    @Synchronized
    fun getTopInterceptedApps(limit: Int = 3): List<AppInterceptStat> {
        if (limit <= 0) return emptyList()
        val totals = LinkedHashMap<String, Pair<String, Int>>()
        for (bucket in hourlyBuckets.values) {
            val prev = totals[bucket.packageName]
            totals[bucket.packageName] = if (prev == null) {
                bucket.appName to bucket.count
            } else {
                bucket.appName to (prev.second + bucket.count)
            }
        }
        return totals.entries
            .map { (pkg, info) ->
                AppInterceptStat(
                    packageName = pkg,
                    appName = info.first,
                    totalCount = info.second
                )
            }
            .sortedByDescending { it.totalCount }
            .take(limit)
    }

    @Synchronized
    fun getTrendSeries(
        range: InterceptTrendRange,
        nowMillis: Long = System.currentTimeMillis()
    ): List<TrendBucket> {
        val snapshot = hourlyBuckets.values.toList()
        return when (range) {
            InterceptTrendRange.DAY -> buildHourlySeries(snapshot, nowMillis, hoursCount = 24)
            InterceptTrendRange.WEEK -> buildDailySeries(snapshot, nowMillis, daysCount = 7)
            InterceptTrendRange.MONTH -> buildDailySeries(snapshot, nowMillis, daysCount = 30)
        }
    }

    @Synchronized
    fun clear() {
        logs.clear()
        _logsFlow.value = emptyList()
        schedulePersistLocked()
    }

    private fun buildHourlySeries(
        snapshot: List<HourlyAppBucket>,
        nowMillis: Long,
        hoursCount: Int
    ): List<TrendBucket> {
        val currentHourStart = (nowMillis / HOUR_MS) * HOUR_MS
        val firstHourStart = currentHourStart - (hoursCount - 1) * HOUR_MS
        val countsByHour = LongArray(hoursCount)
        for (bucket in snapshot) {
            val idx = ((bucket.hourStartMillis - firstHourStart) / HOUR_MS).toInt()
            if (idx in 0 until hoursCount) {
                countsByHour[idx] += bucket.count.toLong()
            }
        }
        return List(hoursCount) { i ->
            val start = firstHourStart + i * HOUR_MS
            TrendBucket(
                startMillis = start,
                endMillis = start + HOUR_MS,
                count = countsByHour[i].coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )
        }
    }

    private fun buildDailySeries(
        snapshot: List<HourlyAppBucket>,
        nowMillis: Long,
        daysCount: Int
    ): List<TrendBucket> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayStarts = LongArray(daysCount + 1)
        cal.add(Calendar.DAY_OF_YEAR, -(daysCount - 1))
        for (i in 0..daysCount) {
            dayStarts[i] = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        val countsByDay = IntArray(daysCount)
        val rangeStart = dayStarts[0]
        val rangeEnd = dayStarts[daysCount]
        for (bucket in snapshot) {
            val t = bucket.hourStartMillis
            if (t < rangeStart || t >= rangeEnd) continue
            for (i in 0 until daysCount) {
                if (t >= dayStarts[i] && t < dayStarts[i + 1]) {
                    countsByDay[i] += bucket.count
                    break
                }
            }
        }
        return List(daysCount) { i ->
            TrendBucket(
                startMillis = dayStarts[i],
                endMillis = dayStarts[i + 1],
                count = countsByDay[i]
            )
        }
    }

    private fun pruneExpiredBucketsLocked(nowMillis: Long) {
        val cutoff = nowMillis - RETENTION_MS
        val iter = hourlyBuckets.entries.iterator()
        while (iter.hasNext()) {
            if (iter.next().value.hourStartMillis < cutoff) {
                iter.remove()
            }
        }
    }

    private fun loadFromDisk(context: Context) {
        val file = File(context.filesDir, HISTORY_FILE_NAME)
        if (!file.exists()) return
        runCatching {
            val raw = file.readText(Charsets.UTF_8)
            if (raw.isBlank()) return
            val root = JSONObject(raw)
            val savedTotal = root.optInt("totalInterceptions", 0)

            val loadedLogs = mutableListOf<AuditLogEntry>()
            val logsArr = root.optJSONArray("logs")
            if (logsArr != null) {
                for (i in 0 until logsArr.length()) {
                    val obj = logsArr.optJSONObject(i) ?: continue
                    val modeName = obj.optString("mode", OpMode.IGNORE.name)
                    val mode = runCatching { OpMode.valueOf(modeName) }.getOrDefault(OpMode.IGNORE)
                    loadedLogs.add(
                        AuditLogEntry(
                            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            packageName = obj.optString("packageName"),
                            appName = obj.optString("appName"),
                            opCode = obj.optInt("opCode", 0),
                            opName = obj.optString("opName"),
                            timestamp = obj.optLong("timestamp", 0L),
                            count = obj.optInt("count", 1),
                            mode = mode
                        )
                    )
                }
            }

            val bucketsArr = root.optJSONArray("buckets")
            if (bucketsArr != null) {
                for (i in 0 until bucketsArr.length()) {
                    val obj = bucketsArr.optJSONObject(i) ?: continue
                    val hourStart = obj.optLong("hourStart", 0L)
                    val pkg = obj.optString("pkg")
                    val appName = obj.optString("appName", pkg)
                    val count = obj.optInt("count", 0)
                    if (pkg.isBlank() || count <= 0) continue
                    val key = "$hourStart|$pkg"
                    hourlyBuckets[key] = HourlyAppBucket(hourStart, pkg, appName, count)
                }
            }

            pruneExpiredBucketsLocked(System.currentTimeMillis())

            logs.clear()
            logs.addAll(loadedLogs.take(MAX_LOG_ENTRIES))
            _logsFlow.value = logs.toList()
            _totalInterceptions.value = maxOf(savedTotal, hourlyBuckets.values.sumOf { it.count })
            _statsRevision.value += 1
        }.onFailure {
            AppLogger.w("Failed to load audit history: ${it.message}")
        }
    }

    private fun schedulePersistLocked() {
        val ctx = appContext ?: return
        val totalSnapshot = _totalInterceptions.value
        val logsSnapshot = logs.toList()
        val bucketsSnapshot = hourlyBuckets.values.map { it.copy() }

        persistExecutor.execute {
            runCatching {
                val root = JSONObject().apply {
                    put("totalInterceptions", totalSnapshot)
                    put("logs", JSONArray().apply {
                        logsSnapshot.forEach { entry ->
                            put(JSONObject().apply {
                                put("id", entry.id)
                                put("packageName", entry.packageName)
                                put("appName", entry.appName)
                                put("opCode", entry.opCode)
                                put("opName", entry.opName)
                                put("timestamp", entry.timestamp)
                                put("count", entry.count)
                                put("mode", entry.mode.name)
                            })
                        }
                    })
                    put("buckets", JSONArray().apply {
                        bucketsSnapshot.forEach { bucket ->
                            put(JSONObject().apply {
                                put("hourStart", bucket.hourStartMillis)
                                put("pkg", bucket.packageName)
                                put("appName", bucket.appName)
                                put("count", bucket.count)
                            })
                        }
                    })
                }
                val targetFile = File(ctx.filesDir, HISTORY_FILE_NAME)
                val tempFile = File(ctx.filesDir, "$HISTORY_FILE_NAME.tmp")
                tempFile.writeText(root.toString(), Charsets.UTF_8)
                if (!tempFile.renameTo(targetFile)) {
                    targetFile.writeText(root.toString(), Charsets.UTF_8)
                    tempFile.delete()
                }
            }.onFailure {
                AppLogger.w("Failed to persist audit history: ${it.message}")
            }
        }
    }
}
