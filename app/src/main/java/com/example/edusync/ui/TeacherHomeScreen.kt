package com.example.edusync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.edusync.ui.theme.BackgroundLight
import com.example.edusync.ui.theme.PrimaryBlue
import com.example.edusync.ui.theme.SuccessGreen
import com.example.edusync.ui.theme.TextDark
import com.example.edusync.ui.theme.TextLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherHomeScreen(
    teacherId: Int,
    onOpenSchedule: () -> Unit,
    onLogout: () -> Unit,
    viewModel: TeacherViewModel = hiltViewModel()
) {
    LaunchedEffect(teacherId) { viewModel.selectTeacher(teacherId) }

    val teacher by viewModel.currentTeacher.collectAsState()
    val courses by viewModel.teacherCourses.collectAsState()
    val scheduleEntries by viewModel.scheduleEntries.collectAsState()
    val assignedEntries = remember(scheduleEntries, teacherId) {
        scheduleEntries.filter { it.teacherId == teacherId }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ana Sayfa", fontWeight = FontWeight.Bold, color = PrimaryBlue) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Cikis", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PrimaryBlue,
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.School, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = teacher?.let { "${it.title} ${it.name} ${it.surname}" } ?: "Yukleniyor",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = teacher?.department?.ifBlank { "Departman belirtilmedi" } ?: "",
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TeacherSummaryTile(
                    title = "Ders",
                    value = courses.size.toString(),
                    icon = Icons.Default.Class,
                    modifier = Modifier.weight(1f)
                )
                TeacherSummaryTile(
                    title = "Atanan Slot",
                    value = assignedEntries.size.toString(),
                    icon = Icons.Default.CalendarMonth,
                    modifier = Modifier.weight(1f)
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Workspaces, contentDescription = null, tint = SuccessGreen)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Program durumu", fontWeight = FontWeight.Bold, color = TextDark)
                            teacher?.let { StatusBadge(it.scheduleStatus, isReadOnly = false) }
                        }
                    }
                    Text(
                        "Haftalik ders programinizi goruntuleyebilir, admin tarafindan gelen program onerilerini Program sekmesinden onaylayabilir veya revize isteyebilirsiniz.",
                        color = TextLight,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = onOpenSchedule,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("PROGRAMI AC", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherSummaryTile(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(110.dp),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryBlue)
            Column {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextDark)
                Text(title, style = MaterialTheme.typography.bodySmall, color = TextLight)
            }
        }
    }
}
