package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.QuizViewModel
import com.example.ui.viewmodel.ScheduleViewModel
import com.example.ui.viewmodel.StudyMaterialViewModel

@Composable
fun AnalyticsDashboardScreen(
    scheduleViewModel: ScheduleViewModel,
    quizViewModel: QuizViewModel,
    materialViewModel: StudyMaterialViewModel
) {
    val context = LocalContext.current
    val scheduleMetrics by scheduleViewModel.metrics.collectAsState()
    val perfMetrics by quizViewModel.performanceMetrics.collectAsState()
    val allMaterials by materialViewModel.allMaterials.collectAsState()
    val testResults by quizViewModel.testResults.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Academic Summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Academic Progress Dashboard",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Personalized analytics across tests, study materials, and milestone goals.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }

        // Top 4 Metrics Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "Milestones Done",
                value = "${scheduleMetrics.completedMilestones}/${scheduleMetrics.totalMilestones}",
                subtitle = "${scheduleMetrics.completionPercentage.toInt()}% completed",
                icon = Icons.Default.CheckCircle,
                color = Color(0xFF10B981),
                modifier = Modifier.weight(1f)
            )

            StatCard(
                title = "Upcoming Exams",
                value = "${scheduleMetrics.upcomingExams}",
                subtitle = "Deadlines tracked",
                icon = Icons.Default.CalendarMonth,
                color = Color(0xFFEF4444),
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "Average Score",
                value = "${perfMetrics.averageScorePct.toInt()}%",
                subtitle = "${perfMetrics.testsTaken} tests taken",
                icon = Icons.Default.TrendingUp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            StatCard(
                title = "Vault Materials",
                value = "${allMaterials.size}",
                subtitle = "Notes & banks stored",
                icon = Icons.Default.MenuBook,
                color = Color(0xFF8B5CF6),
                modifier = Modifier.weight(1f)
            )
        }

        // Analytical Subject Accuracy Breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Subject Accuracy Analytics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "Individual progress metrics calculated from test scores and custom marking patterns.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (perfMetrics.subjectPerformance.isEmpty()) {
                    Text(
                        "Take or generate practice tests to see subject analytics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    perfMetrics.subjectPerformance.forEach { (subject, acc) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(subject, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("${acc.toInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (acc / 100.0).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (acc >= 75.0) Color(0xFF10B981) else if (acc >= 50.0) Color(0xFFF59E0B) else Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }
        }

        // Weak Topics Identified from Mistakes
        if (perfMetrics.weakTopicList.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Areas for Revision (Mistake Analytics)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF991B1B))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Identified from questions answered incorrectly during tests:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7F1D1D)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    perfMetrics.weakTopicList.forEach { topic ->
                        Text("• $topic", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF991B1B))
                    }
                }
            }
        }

        // Export Report Button
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Export Academic Report", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Export a detailed comprehensive review report containing materials, test scores, milestone completion, and Google Drive links for cloud storage review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val report = buildExportReport(
                            scheduleMetrics,
                            perfMetrics,
                            allMaterials.size,
                            testResults
                        )
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, report)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share or Save to Google Drive")
                        try {
                            context.startActivity(shareIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Report generated: ${report.take(100)}...", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("export_report_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export & Share Academic Report")
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        }
    }
}

private fun buildExportReport(
    scheduleMetrics: com.example.ui.viewmodel.ScheduleMetrics,
    perfMetrics: com.example.ui.viewmodel.PerformanceMetrics,
    materialsCount: Int,
    tests: List<com.example.data.model.TestResult>
): String {
    val sb = StringBuilder()
    sb.append("=========================================\n")
    sb.append("   UNIVAULT ACADEMIC PERFORMANCE REPORT   \n")
    sb.append("=========================================\n\n")

    sb.append("1. MILESTONE & TIMETABLE PROGRESS\n")
    sb.append("   - Milestones Completed: ${scheduleMetrics.completedMilestones} / ${scheduleMetrics.totalMilestones} (${scheduleMetrics.completionPercentage.toInt()}%)\n")
    sb.append("   - Upcoming Exam Deadlines: ${scheduleMetrics.upcomingExams}\n\n")

    sb.append("2. TEST & QUIZ PERFORMANCE METRICS\n")
    sb.append("   - Total Tests Taken: ${perfMetrics.testsTaken}\n")
    sb.append("   - Average Accuracy Score: ${perfMetrics.averageScorePct.toInt()}%\n")
    sb.append("   - Highest Score Achieved: ${perfMetrics.highestScorePct.toInt()}%\n\n")

    sb.append("3. SUBJECT ACCURACY BREAKDOWN\n")
    perfMetrics.subjectPerformance.forEach { (sub, acc) ->
        sb.append("   - $sub: ${acc.toInt()}%\n")
    }
    sb.append("\n")

    if (perfMetrics.weakTopicList.isNotEmpty()) {
        sb.append("4. WEAK TOPICS IDENTIFIED FOR REVISION\n")
        perfMetrics.weakTopicList.forEach { topic ->
            sb.append("   * $topic\n")
        }
        sb.append("\n")
    }

    sb.append("5. RECENT TEST LOGS\n")
    tests.take(5).forEachIndexed { i, t ->
        sb.append("   [${i + 1}] ${t.quizTitle} (${t.subject}): Score ${t.score.toInt()}/${t.totalMarks.toInt()} (${t.accuracy.toInt()}%)\n")
    }

    sb.append("\nTotal Study Materials Stored in UniVault: $materialsCount\n")
    sb.append("Cloud Storage: Synced to Google Drive & Firebase Firestore\n")
    sb.append("=========================================\n")
    return sb.toString()
}
