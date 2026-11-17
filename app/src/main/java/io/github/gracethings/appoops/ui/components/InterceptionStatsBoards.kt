package io.github.gracethings.appoops.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gracethings.appoops.R
import io.github.gracethings.appoops.data.model.AppInterceptStat
import io.github.gracethings.appoops.data.model.InterceptTrendRange
import io.github.gracethings.appoops.data.model.TrendBucket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * M3E-styled board displaying the top 3 most frequently intercepted applications.
 */
@Composable
fun TopInterceptedAppsBoard(
    topApps: List<AppInterceptStat>,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.stats_top_apps_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (topApps.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.stats_top_apps_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val maxCount = topApps.maxOf { it.totalCount }.coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    topApps.forEachIndexed { index, stat ->
                        TopInterceptAppRow(
                            rank = index + 1,
                            stat = stat,
                            maxCount = maxCount,
                            onClick = { onAppClick(stat.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopInterceptAppRow(
    rank: Int,
    stat: AppInterceptStat,
    maxCount: Int,
    onClick: () -> Unit
) {
    val badgeBg = when (rank) {
        1 -> MaterialTheme.colorScheme.primaryContainer
        2 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val badgeFg = when (rank) {
        1 -> MaterialTheme.colorScheme.onPrimaryContainer
        2 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    val barColor = when (rank) {
        1 -> MaterialTheme.colorScheme.primary
        2 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }

    val targetFraction = (stat.totalCount.toFloat() / maxCount.toFloat()).coerceIn(0.08f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "top_app_bar_$rank"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = badgeFg
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        AppIcon(
            packageName = stat.packageName,
            contentDescription = stat.appName,
            size = 36.dp,
            cornerRadius = 10.dp,
            fallbackIconSize = 20.dp
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stat.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = badgeBg.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = stringResource(R.string.audit_count_format, stat.totalCount),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(barColor)
                )
            }
        }
    }
}

/**
 * M3E-styled board rendering the total interception trend curve over 1 day / 1 week / 1 month.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterceptionTrendBoard(
    buckets: List<TrendBucket>,
    selectedRange: InterceptTrendRange,
    onSelectRange: (InterceptTrendRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val rangeTotal = remember(buckets) { buckets.sumOf { it.count } }
    var inspectedIndex by remember(selectedRange, buckets) { mutableStateOf<Int?>(null) }

    val timeFormatter = remember(selectedRange) {
        when (selectedRange) {
            InterceptTrendRange.DAY -> SimpleDateFormat("HH:mm", Locale.getDefault())
            InterceptTrendRange.WEEK,
            InterceptTrendRange.MONTH -> SimpleDateFormat("MM/dd", Locale.getDefault())
        }
    }

    val inspectedBucket = inspectedIndex?.let { idx -> buckets.getOrNull(idx) }
    val headerCount = inspectedBucket?.count ?: rangeTotal

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.stats_trend_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitleText = if (inspectedBucket != null) {
                        "${timeFormatter.format(Date(inspectedBucket.startMillis))} • " +
                            stringResource(R.string.audit_count_format, inspectedBucket.count)
                    } else {
                        stringResource(R.string.stats_range_total_format, rangeTotal)
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                ) {
                    AnimatedContent(
                        targetState = headerCount,
                        transitionSpec = {
                            fadeIn(tween(180)).togetherWith(fadeOut(tween(120)))
                        },
                        label = "trend_count_anim"
                    ) { targetCount ->
                        Text(
                            text = targetCount.toString(),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // M3E Segmented range selector
            val ranges = remember {
                listOf(
                    InterceptTrendRange.DAY to R.string.stats_range_day,
                    InterceptTrendRange.WEEK to R.string.stats_range_week,
                    InterceptTrendRange.MONTH to R.string.stats_range_month
                )
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ranges.forEachIndexed { index, (range, labelRes) ->
                    SegmentedButton(
                        selected = selectedRange == range,
                        onClick = {
                            if (selectedRange != range) {
                                onSelectRange(range)
                            } else {
                                inspectedIndex = null
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ranges.size
                        ),
                        icon = {}
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedRange == range) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Smooth Trend Curve Chart
            TrendCurveCanvas(
                buckets = buckets,
                rangeKey = selectedRange,
                rangeTotal = rangeTotal,
                inspectedIndex = inspectedIndex,
                onInspectIndex = { inspectedIndex = it },
                timeFormatter = timeFormatter
            )
        }
    }
}

@Composable
private fun TrendCurveCanvas(
    buckets: List<TrendBucket>,
    rangeKey: InterceptTrendRange,
    rangeTotal: Int,
    inspectedIndex: Int?,
    onInspectIndex: (Int?) -> Unit,
    timeFormatter: SimpleDateFormat
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val secondaryAccent = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val surfaceColor = MaterialTheme.colorScheme.surface

    var revealTarget by remember(rangeKey) { mutableFloatStateOf(0f) }
    LaunchedEffect(rangeKey) {
        revealTarget = 1f
    }
    val revealProgress by animateFloatAsState(
        targetValue = revealTarget,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "curve_reveal"
    )

    val rawMax = remember(buckets) { buckets.maxOfOrNull { it.count } ?: 0 }
    val niceMax = remember(rawMax) { computeNiceYAxisMax(rawMax) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.52f))
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Y-axis reference labels
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = niceMax.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                    Text(
                        text = (niceMax / 2).toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }

                // Curve Canvas
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(buckets) {
                            if (buckets.isEmpty()) return@pointerInput
                            detectTapGestures { offset ->
                                val idx = mapOffsetToBucketIndex(offset.x, size.width.toFloat(), buckets.size)
                                onInspectIndex(if (inspectedIndex == idx) null else idx)
                            }
                        }
                        .pointerInput(buckets) {
                            if (buckets.isEmpty()) return@pointerInput
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    onInspectIndex(mapOffsetToBucketIndex(offset.x, size.width.toFloat(), buckets.size))
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    onInspectIndex(
                                        mapOffsetToBucketIndex(
                                            change.position.x,
                                            size.width.toFloat(),
                                            buckets.size
                                        )
                                    )
                                }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val topPad = 6.dp.toPx()
                    val bottomPad = 6.dp.toPx()
                    val chartH = (h - topPad - bottomPad).coerceAtLeast(1f)
                    val baseY = topPad + chartH

                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    for (step in 0..2) {
                        val y = topPad + chartH * (step / 2f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (step < 2) dashEffect else null
                        )
                    }

                    if (buckets.isEmpty()) return@Canvas

                    val stepX = if (buckets.size > 1) w / (buckets.size - 1).toFloat() else 0f
                    val points = buckets.mapIndexed { idx, bucket ->
                        val x = if (buckets.size > 1) idx * stepX else w / 2f
                        val ratio = (bucket.count.toFloat() / niceMax.toFloat()).coerceIn(0f, 1f)
                        val y = baseY - ratio * chartH
                        Offset(x, y)
                    }

                    val strokePath = Path()
                    val fillPath = Path()
                    points.forEachIndexed { idx, pt ->
                        if (idx == 0) {
                            strokePath.moveTo(pt.x, pt.y)
                            fillPath.moveTo(pt.x, baseY)
                            fillPath.lineTo(pt.x, pt.y)
                        } else {
                            val prev = points[idx - 1]
                            val midX = (prev.x + pt.x) / 2f
                            strokePath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                            fillPath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                        }
                    }
                    fillPath.lineTo(points.last().x, baseY)
                    fillPath.close()

                    clipRect(right = (w * revealProgress).coerceAtLeast(1f)) {
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    lineColor.copy(alpha = 0.28f),
                                    lineColor.copy(alpha = 0.02f)
                                ),
                                startY = topPad,
                                endY = baseY
                            )
                        )
                        drawPath(
                            path = strokePath,
                            color = lineColor,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Draw inspected vertical guide & dot, or highlight peak dot if non-zero
                    val highlightIdx = inspectedIndex ?: if (rawMax > 0) {
                        buckets.indexOfFirst { it.count == rawMax }.takeIf { it >= 0 }
                    } else null

                    if (highlightIdx != null && highlightIdx in points.indices) {
                        val pt = points[highlightIdx]
                        if (inspectedIndex != null) {
                            drawLine(
                                color = secondaryAccent.copy(alpha = 0.55f),
                                start = Offset(pt.x, topPad),
                                end = Offset(pt.x, baseY),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = dashEffect
                            )
                        }
                        val dotColor = if (inspectedIndex != null) secondaryAccent else lineColor
                        drawCircle(
                            color = dotColor.copy(alpha = 0.22f),
                            radius = 9.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = surfaceColor,
                            radius = 5.5.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = dotColor,
                            radius = 3.5.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }

            if (rangeTotal == 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = stringResource(R.string.stats_trend_empty),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // X-axis tick labels
        if (buckets.isNotEmpty()) {
            val startLabel = timeFormatter.format(Date(buckets.first().startMillis))
            val midLabel = timeFormatter.format(Date(buckets[buckets.size / 2].startMillis))
            val endLabel = timeFormatter.format(Date(buckets.last().startMillis))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = startLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Text(
                    text = midLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Text(
                    text = endLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun mapOffsetToBucketIndex(x: Float, width: Float, bucketCount: Int): Int {
    if (bucketCount <= 1 || width <= 0f) return 0
    val ratio = (x / width).coerceIn(0f, 1f)
    return (ratio * (bucketCount - 1)).roundToInt().coerceIn(0, bucketCount - 1)
}

private fun computeNiceYAxisMax(rawMax: Int): Int {
    if (rawMax <= 4) return 4
    val step = when {
        rawMax <= 10 -> 2
        rawMax <= 50 -> 10
        rawMax <= 200 -> 25
        rawMax <= 1000 -> 100
        else -> 500
    }
    return ceil(rawMax.toDouble() / step.toDouble()).toInt() * step
}
