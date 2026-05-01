package com.example.edusync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.edusync.data.ScheduleStatus
import com.example.edusync.data.Teacher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherManagementScreen(
    onNavigateBack: (() -> Unit)? = null,
    onTeacherClick: (Int) -> Unit,
    viewModel: TeacherViewModel = hiltViewModel()
) {
    val teachers by viewModel.teachers.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var teacherToDelete by remember { mutableStateOf<Teacher?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hoca Yönetimi") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    }
                },
                actions = {
                    if (teachers.isNotEmpty()) {
                        TextButton(
                            onClick = { showDeleteAllDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tumunu Sil")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Hoca Ekle")
            }
        }
    ) { padding ->
        if (teachers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Henüz hoca eklenmemiş.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(teachers) { teacher ->
                    TeacherItem(
                        teacher = teacher,
                        onClick = { onTeacherClick(teacher.id) },
                        onDelete = { teacherToDelete = teacher }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddTeacherDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, surname ->
                    viewModel.addTeacher(name, surname)
                    showAddDialog = false
                }
            )
        }

        teacherToDelete?.let { teacher ->
            AlertDialog(
                onDismissRequest = { teacherToDelete = null },
                title = { Text("Hocayı Sil") },
                text = { Text("${teacher.name} ${teacher.surname} isimli hocayı silmek istediğinize emin misiniz?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteTeacher(teacher)
                            teacherToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("SİL") }
                },
                dismissButton = {
                    TextButton(onClick = { teacherToDelete = null }) { Text("İPTAL") }
                }
            )
        }

        if (showDeleteAllDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAllDialog = false },
                title = { Text("Tum Hocalari Sil") },
                text = { Text("Tum hocalar, hoca hesaplari, gecici sifreler, dersler ve program atamalari silinecek. Emin misiniz?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteAllTeachers()
                            showDeleteAllDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("TUMUNU SIL") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllDialog = false }) { Text("IPTAL") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherItem(
    teacher: Teacher,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (teacher.scheduleStatus == ScheduleStatus.PENDING) 
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(32.dp))
                if (teacher.scheduleStatus == ScheduleStatus.PENDING) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Red, CircleShape)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "${teacher.title} ${teacher.name} ${teacher.surname}", style = MaterialTheme.typography.titleMedium)
                val statusText = when(teacher.scheduleStatus) {
                    ScheduleStatus.PENDING -> "Yeni İstek Var!"
                    ScheduleStatus.APPROVED -> "Onaylı"
                    ScheduleStatus.REJECTED -> "Reddedildi"
                    ScheduleStatus.ADMIN_PROPOSAL -> "Öneri Bekliyor"
                }
                Text(
                    text = statusText, 
                    style = MaterialTheme.typography.bodySmall,
                    color = if (teacher.scheduleStatus == ScheduleStatus.PENDING) Color.Red else Color.Gray
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun AddTeacherDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Hoca Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ad") })
                OutlinedTextField(value = surname, onValueChange = { surname = it }, label = { Text("Soyad") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && surname.isNotBlank()) onConfirm(name, surname) },
                enabled = name.isNotBlank() && surname.isNotBlank()
            ) { Text("Ekle") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}
