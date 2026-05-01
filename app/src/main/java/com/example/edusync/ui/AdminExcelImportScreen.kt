package com.example.edusync.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.edusync.data.TemporaryCredential

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminExcelImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val importState by viewModel.importState.collectAsState(initial = null)
    val previewItems by viewModel.excelPreview.collectAsState(initial = null)
    val temporaryCredentials by viewModel.temporaryCredentials.collectAsState(initial = emptyList())
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadPreview(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Excel İçe Aktar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val currentPreview = previewItems
            
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentPreview == null) {
                    Spacer(modifier = Modifier.weight(0.45f))
                    Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Ders ve Hoca Listesi Yükle", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Excel Formatı: Course Code | Course Name | Lecturer", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = { launcher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("DOSYA SEÇ")
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    TemporaryCredentialsSection(temporaryCredentials)
                    Spacer(modifier = Modifier.weight(0.35f))
                } else {
                    Text("Excel Önizleme (İlk 5 Satır)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Card(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(modifier = Modifier.padding(8.dp)) {
                            items(currentPreview) { item ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text("${item.courseCode}: ${item.courseName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Hoca: ${item.lecturer}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(onClick = { viewModel.clearImportState() }, modifier = Modifier.weight(1f)) {
                            Text("İPTAL")
                        }
                        Button(onClick = { viewModel.confirmImport(context) }, modifier = Modifier.weight(1f)) {
                            Text("ONAYLA VE AKTAR")
                        }
                    }
                }
            }

            when (val state = importState) {
                is ImportResult.Loading -> {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ImportResult.Success -> {
                    if (state.generatedCredentials.isNotEmpty()) {
                        AlertDialog(
                            onDismissRequest = { viewModel.clearImportState() },
                            icon = { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50)) },
                            title = { Text("Basarili") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("${state.count} kayit basariyla sisteme aktarildi.")
                                    Text(
                                        "Olusturulan hoca hesaplari",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 220.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(state.generatedCredentials) { credential ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                shape = MaterialTheme.shapes.small,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(credential.teacherName, fontWeight = FontWeight.Bold)
                                                    Text("Kullanici adi: ${credential.username}")
                                                    Text("Gecici sifre: ${credential.initialPassword}")
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { viewModel.clearImportState() }) { Text("Tamam") } }
                        )
                    } else {
                    AlertDialog(
                        onDismissRequest = { viewModel.clearImportState() },
                        icon = { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50)) },
                        title = { Text("Başarılı") },
                        text = { Text("${state.count} kayıt başarıyla sisteme aktarıldı.") },
                        confirmButton = { TextButton(onClick = { viewModel.clearImportState() }) { Text("Tamam") } }
                    )
                }
                    }
                is ImportResult.Error -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.clearImportState() },
                        title = { Text("Hata") },
                        text = { Text(state.message) },
                        confirmButton = { TextButton(onClick = { viewModel.clearImportState() }) { Text("Kapat") } }
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun TemporaryCredentialsSection(credentials: List<TemporaryCredential>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Gecici hoca hesaplari",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (credentials.isEmpty()) {
                Text(
                    "Excel import sonrasi olusan gecici hesaplar burada gorunecek.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(credentials) { credential ->
                        TemporaryCredentialRow(credential)
                    }
                }
            }
        }
    }
}

@Composable
private fun TemporaryCredentialRow(credential: TemporaryCredential) {
    val statusText = if (credential.consumed) {
        "Sifre degistirildi - hesap aktif"
    } else {
        "Ilk giris bekleniyor"
    }
    val statusColor = if (credential.consumed) Color(0xFF2E7D32) else Color(0xFFEF6C00)
    val statusIcon = if (credential.consumed) Icons.Default.TaskAlt else Icons.Default.PendingActions

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = statusColor.copy(alpha = 0.08f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(statusText, color = statusColor, fontWeight = FontWeight.Bold)
            }
            Text(credential.teacherName, fontWeight = FontWeight.Bold)
            Text("Kullanici adi: ${credential.username}")
            if (!credential.consumed) {
                Text("Gecici sifre: ${credential.temporaryPassword}")
            }
        }
    }
}
