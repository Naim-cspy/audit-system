package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ProfitPrediction
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.SupermarketGreen
import com.example.ui.theme.SupermarketGreenDark
import com.example.ui.theme.SupermarketGreenLight
import com.example.ui.viewmodel.AdminViewModel

@Composable
fun AnalyticsScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val prediction by adminViewModel.profitPrediction.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Statistical Profit AI & Forecast",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = SupermarketGreenDark
                    )
                    Text(
                        text = "Linear regression run-rate & future projection",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                IconButton(
                    onClick = { adminViewModel.refreshAnalytics() },
                    modifier = Modifier.testTag("refresh_analytics_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = SupermarketGreen)
                }
            }
        }

        // Regression Formula Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = SupermarketGreenLight),
                modifier = Modifier.fillMaxWidth().testTag("formula_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Calculate, contentDescription = null, tint = SupermarketGreenDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Linear Regression Model",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = SupermarketGreenDark
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = prediction.equation.ifEmpty { "Profit = m * Day + b" },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SupermarketGreenDark
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Daily Growth Slope: \$${"%.2f".format(prediction.dailySlope)} / day",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF155724)
                    )
                }
            }
        }

        // Projections Row (30d, 90d, 1yr)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProjectionCard(
                    period = "30 Days",
                    estimated = "\$${"%.2f".format(prediction.prediction30d)}",
                    modifier = Modifier.weight(1f).testTag("pred_30d")
                )
                ProjectionCard(
                    period = "90 Days",
                    estimated = "\$${"%.2f".format(prediction.prediction90d)}",
                    modifier = Modifier.weight(1f).testTag("pred_90d")
                )
                ProjectionCard(
                    period = "1 Year",
                    estimated = "\$${"%.2f".format(prediction.prediction1yr)}",
                    modifier = Modifier.weight(1f).testTag("pred_1yr")
                )
            }
        }

        // Custom Canvas Chart
        item {
            ElevatedCard(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("profit_trend_chart_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Budget Growth & Trend Line",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(SupermarketGreen, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Actual", style = MaterialTheme.typography.labelSmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(AccentBlue, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Trend", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (prediction.actualProfits.isNotEmpty()) {
                        ProfitRegressionChart(
                            profits = prediction.actualProfits,
                            trend = prediction.trendLine,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Requires at least 2 entries to compute regression trend.",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Data Points Table
        item {
            ElevatedCard(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("data_points_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Historical Data Points (${prediction.dates.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    prediction.dates.take(8).forEachIndexed { index, date ->
                        val actual = prediction.actualProfits.getOrNull(index) ?: 0.0
                        val trendVal = prediction.trendLine.getOrNull(index) ?: 0.0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(date, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text("Actual: \$${"%.2f".format(actual)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = SupermarketGreen))
                            Text("Trend: \$${"%.2f".format(trendVal)}", style = MaterialTheme.typography.bodySmall.copy(color = AccentBlue))
                        }
                        if (index < prediction.dates.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectionCard(
    period: String,
    estimated: String,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = period.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = estimated,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = SupermarketGreen
                )
            )
            Text(
                text = "Projected",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun ProfitRegressionChart(
    profits: List<Double>,
    trend: List<Double>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 36f

        val effectiveWidth = width - (padding * 2)
        val effectiveHeight = height - (padding * 2)

        val allValues = profits + trend
        val minVal = (allValues.minOrNull() ?: 0.0).toFloat()
        val maxVal = (allValues.maxOrNull() ?: 100.0).toFloat()
        val range = if (maxVal - minVal == 0f) 1f else maxVal - minVal

        val count = profits.size
        if (count < 2) return@Canvas

        val stepX = effectiveWidth / (count - 1)

        // Draw grid lines
        for (i in 0..4) {
            val y = padding + (effectiveHeight / 4) * i
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(padding, y),
                end = Offset(width - padding, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw Trend Line (Blue dashed)
        val trendPath = Path()
        trend.forEachIndexed { index, value ->
            val x = padding + index * stepX
            val normalizedY = (value.toFloat() - minVal) / range
            val y = padding + effectiveHeight - (normalizedY * effectiveHeight)
            if (index == 0) trendPath.moveTo(x, y) else trendPath.lineTo(x, y)
        }

        drawPath(
            path = trendPath,
            color = Color(0xFF0288D1),
            style = Stroke(
                width = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
            )
        )

        // Draw Actual Profits Line (Green solid)
        val actualPath = Path()
        profits.forEachIndexed { index, value ->
            val x = padding + index * stepX
            val normalizedY = (value.toFloat() - minVal) / range
            val y = padding + effectiveHeight - (normalizedY * effectiveHeight)
            if (index == 0) actualPath.moveTo(x, y) else actualPath.lineTo(x, y)
        }

        drawPath(
            path = actualPath,
            color = Color(0xFF1E7E34),
            style = Stroke(width = 3.5.dp.toPx())
        )

        // Draw Actual Profit Points (Green dots)
        profits.forEachIndexed { index, value ->
            val x = padding + index * stepX
            val normalizedY = (value.toFloat() - minVal) / range
            val y = padding + effectiveHeight - (normalizedY * effectiveHeight)
            drawCircle(
                color = Color(0xFF155724),
                radius = 5.dp.toPx(),
                center = Offset(x, y)
            )
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}
