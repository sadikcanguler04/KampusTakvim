package com.example.edusync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.edusync.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit,
    onNavigateToExcel: () -> Unit,
    onNavigateToCodes: () -> Unit,
    onNavigateToClassrooms: () -> Unit = {},
    onNavigateToAssignments: () -> Unit = {},
    viewModel: AdminViewModel = hiltViewModel()
) {
    val unassignedTeachers by viewModel.unassignedTeachers.collectAsState()
    val unassignedCourses by viewModel.unassignedCourses.collectAsState()
    val availableClassrooms by viewModel.availableClassrooms.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("KampusTakvim Yönetim", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Çıkış Yap", tint = ErrorRed)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundLight),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hoşgeldin Kartı
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryBlue)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Hoşgeldiniz,", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                        Text("Sistem Yöneticisi", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Tüm akademik takvimi ve hoca planlamalarını buradan tek tıkla yönetebilirsiniz.", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }
            }

            // Hızlı Erişim Grid
            item {
                Text("Hızlı Erişim", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 18.sp)
            }

            item {
                // 2x2 Grid
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            QuickActionCard(
                                title = "Excel Aktar",
                                subtitle = "Toplu Veri Yükle",
                                icon = Icons.Default.FileUpload,
                                color = SuccessGreen,
                                onClick = onNavigateToExcel
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            QuickActionCard(
                                title = "Kayıt Kodları",
                                subtitle = "Hoca Yetkilendirme",
                                icon = Icons.Default.VpnKey,
                                color = WarningOrange,
                                onClick = onNavigateToCodes
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            QuickActionCard(
                                title = "Sınıflar",
                                subtitle = "Sınıf Yönetimi",
                                icon = Icons.Default.MeetingRoom,
                                color = SecondaryBlue,
                                onClick = onNavigateToClassrooms
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            QuickActionCard(
                                title = "Atamalar",
                                subtitle = "Ders Programı",
                                icon = Icons.Default.Assignment,
                                color = DeepBlue,
                                onClick = onNavigateToAssignments
                            )
                        }
                    }
                }
            }

            // ========== Phase 2 Madde 6: Dashboard Özet Panelleri ==========

            item {
                Spacer(Modifier.height(8.dp))
                Text("Durum Paneli", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 18.sp)
            }

            // Panel 1: Atanmamış Hocalar
            item {
                DashboardSummaryPanel(
                    title = "Atanmamış Hocalar",
                    icon = Icons.Default.PersonOff,
                    color = ErrorRed,
                    count = unassignedTeachers.size,
                    emptyMessage = "Tüm hocalar en az 1 derse atanmış ✓"
                ) {
                    unassignedTeachers.forEach { teacher ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = ErrorRed.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Person, null, tint = ErrorRed, modifier = Modifier.padding(6.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("${teacher.title} ${teacher.name} ${teacher.surname}", fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(teacher.department.ifEmpty { "Bölüm belirtilmemiş" }, fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // Panel 2: Atanmamış Dersler
            item {
                DashboardSummaryPanel(
                    title = "Atanmamış Dersler",
                    icon = Icons.Default.BookmarkRemove,
                    color = WarningOrange,
                    count = unassignedCourses.size,
                    emptyMessage = "Tüm dersler programa konulmuş ✓"
                ) {
                    unassignedCourses.forEach { course ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = WarningOrange.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Book, null, tint = WarningOrange, modifier = Modifier.padding(6.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(course.code, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(course.name, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // Panel 3: Müsait Sınıflar
            item {
                DashboardSummaryPanel(
                    title = "Müsait Sınıflar",
                    icon = Icons.Default.EventAvailable,
                    color = SuccessGreen,
                    count = availableClassrooms.size,
                    emptyMessage = "Hiç sınıf kaydedilmemiş"
                ) {
                    availableClassrooms.forEach { (classroom, freeSlots) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = SuccessGreen.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.MeetingRoom, null, tint = SuccessGreen, modifier = Modifier.padding(6.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(classroom.roomCode, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("${classroom.capacity} kişilik", fontSize = 11.sp, color = Color.Gray)
                            }
                            Surface(
                                color = SuccessGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "$freeSlots boş slot",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun DashboardSummaryPanel(
    title: String,
    icon: ImageVector,
    color: Color,
    count: Int,
    emptyMessage: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark, modifier = Modifier.weight(1f))
                Surface(
                    color = if (count > 0) color.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        if (count > 0) "$count" else "✓",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (count > 0) color else SuccessGreen
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Divider(color = Color.LightGray.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))

            if (count == 0) {
                Text(emptyMessage, color = SuccessGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            } else {
                content()
            }
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(140.dp).clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Surface(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(10.dp))
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
                Text(subtitle, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}
