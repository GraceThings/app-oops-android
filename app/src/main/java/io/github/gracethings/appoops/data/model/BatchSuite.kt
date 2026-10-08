package io.github.gracethings.appoops.data.model

import androidx.annotation.StringRes
import io.github.gracethings.appoops.R

enum class BatchSuiteType(
    val title: String,
    val description: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int
) {
    SENSOR(
        title = "Disable Accelerometer/Gyroscope",
        description = "Blocks accelerometer and gyroscope sensors (IGNORE) to eliminate unauthorized motion and movement tracking",
        titleRes = R.string.suite_sensor_title,
        descRes = R.string.suite_sensor_desc
    ),
    CLIPBOARD(
        title = "Intercept Read/Write Clipboard",
        description = "Intercepts reading and writing clipboard to prevent third-party snooping",
        titleRes = R.string.suite_clipboard_title,
        descRes = R.string.suite_clipboard_desc
    ),
    QUERY_AND_USAGE(
        title = "Block App List Reading & Usage Stats",
        description = "Intercepts installed app scanning and usage stats to cut off device profiling",
        titleRes = R.string.suite_query_usage_title,
        descRes = R.string.suite_query_usage_desc
    ),
    BACKGROUND(
        title = "Background Restriction",
        description = "Restricts background running, wake locks, and boot completion broadcast",
        titleRes = R.string.suite_background_title,
        descRes = R.string.suite_background_desc
    )
}

