package com.example.edusync.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.edusync.data.AssignmentRequest
import com.example.edusync.data.AssignmentRequestStatus
import com.example.edusync.data.Classroom
import com.example.edusync.data.Course
import com.example.edusync.data.Teacher
import com.example.edusync.ui.theme.*

private data class PendingAssignment(
    val course: Course,
    val teacher: Teacher,
    val classroom: Classroom,
    val day: Int,
    val timeSlot: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val scheduleEntries by viewModel.scheduleEntries.collectAsState()
    val teachers by viewModel.teachers.collectAsState()
    val courses by viewModel.allCourses.collectAsState()
    val classrooms by viewModel.classrooms.collectAsState()
    val assignmentRequests by viewModel.assignmentRequests.collectAsState()
    val error by viewModel.assignmentError.collectAsState()
    val success by viewModel.assignmentSuccess.collectAsState()
    val isAssigning by viewModel.isAssigning.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var pendingAssignment by remember { mutableStateOf<PendingAssignment?>(null) }

    val days = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma")
    val dayShorts = listOf("Pzt", "Sal", "Çar", "Per", "Cum")
    val timeSlots = listOf(
        "08:30 - 09:20", "09:30 - 10:20", "10:30 - 11:20", "11:30 - 12:20",
        "13:30 - 14:20", "14:30 - 15:20", "15:30 - 16:20", "16:30 - 17:20"
    )
    val activeAssignmentRequests = remember(assignmentRequests) {
        assignmentRequests.filter {
            it.status == AssignmentRequestStatus.PENDING || it.status == AssignmentRequestStatus.REJECTED
        }
    }
    val pendingRequestCount = activeAssignmentRequests.count { it.status == AssignmentRequestStatus.PENDING }
    val rejectedRequestCount = activeAssignmentRequests.count { it.status == AssignmentRequestStatus.REJECTED }

    LaunchedEffect(success) {
        if (success) {
            showAddDialog = false
            viewModel.clearAssignmentSuccess()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ders Atamaları", fontWeight = FontWeight.Bold, color = PrimaryBlue) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = PrimaryBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.clearAssignmentError()
                    showAddDialog = true
                },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, "Atama Yap")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundLight)
        ) {
            // Summary
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SuccessGreen)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Assignment, null, tint = Color.White, modifier = Modifier.padding(12.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Toplam Atama", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        Text("${scheduleEntries.size} Kesin Ders", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Bekleyen: $pendingRequestCount",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (rejectedRequestCount > 0) {
                                Text(
                                    "Reddedilen: $rejectedRequestCount",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            if (scheduleEntries.isEmpty() && activeAssignmentRequests.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Assignment, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Henüz atama yapılmamış", color = Color.Gray, fontWeight = FontWeight.Medium)
                        Text("Sağ alttaki + butonuna tıklayarak atayın", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = scheduleEntries, key = { it.id }) { entry ->
                        val teacher = teachers.find { it.id == entry.teacherId }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Day/Time badge
                                Surface(
                                    color = PrimaryBlue,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(dayShorts.getOrElse(entry.day) { "?" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(timeSlots.getOrElse(entry.timeSlot) { "?" }, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${entry.courseCode} - ${entry.courseName}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    teacher?.let {
                                        Text("${it.title} ${it.name} ${it.surname}", fontSize = 12.sp, color = Color.Gray)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.MeetingRoom, null, tint = PrimaryBlue, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(entry.classroomId, fontSize = 12.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium)
                                    }
                                }
                                IconButton(onClick = { showDeleteDialog = entry.id }) {
                                    Icon(Icons.Default.Delete, "Sil", tint = ErrorRed.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                    if (activeAssignmentRequests.isNotEmpty()) {
                        item {
                            Text(
                                "Hoca Onayi Bekleyenler",
                                modifier = Modifier.padding(top = if (scheduleEntries.isEmpty()) 0.dp else 10.dp, bottom = 2.dp),
                                color = TextDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        items(items = activeAssignmentRequests, key = { "request_${it.id}" }) { request ->
                            AssignmentRequestCard(
                                request = request,
                                teachers = teachers,
                                dayShorts = dayShorts,
                                timeSlots = timeSlots
                            )
                        }
                    }
                }
            }
        }

        // Add Assignment Dialog
        if (showAddDialog) {
            AssignmentDialog(
                teachers = teachers,
                courses = courses,
                classrooms = classrooms,
                days = days,
                timeSlots = timeSlots,
                error = error,
                isAssigning = isAssigning,
                onDismiss = {
                    showAddDialog = false
                    viewModel.clearAssignmentError()
                },
                onConfirm = { course, teacher, classroom, day, timeSlot ->
                    pendingAssignment = PendingAssignment(course, teacher, classroom, day, timeSlot)
                }
            )
        }

        pendingAssignment?.let { assignment ->
            AssignmentModeDialog(
                assignment = assignment,
                days = days,
                timeSlots = timeSlots,
                isAssigning = isAssigning,
                onDismiss = { pendingAssignment = null },
                onSendProposal = {
                    viewModel.proposeScheduleToTeacher(
                        assignment.course.code,
                        assignment.course.name,
                        assignment.teacher.id,
                        assignment.classroom.roomCode,
                        assignment.day,
                        assignment.timeSlot
                    )
                    pendingAssignment = null
                },
                onForceAssign = {
                    viewModel.assignSchedule(
                        assignment.course.code,
                        assignment.course.name,
                        assignment.teacher.id,
                        assignment.classroom.roomCode,
                        assignment.day,
                        assignment.timeSlot
                    )
                    pendingAssignment = null
                }
            )
        }

        // Delete Confirmation
        if (showDeleteDialog != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Atamayı Sil", fontWeight = FontWeight.Bold) },
                text = { Text("Bu ders atamasını silmek istediğinize emin misiniz?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteScheduleEntry(showDeleteDialog!!)
                            showDeleteDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("SİL") }
                },
                dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("İPTAL") } }
            )
        }
    }
}

@Composable
private fun AssignmentRequestCard(
    request: AssignmentRequest,
    teachers: List<Teacher>,
    dayShorts: List<String>,
    timeSlots: List<String>
) {
    val isRejected = request.status == AssignmentRequestStatus.REJECTED
    val statusColor = if (isRejected) ErrorRed else Color(0xFFFFA000)
    val statusText = if (isRejected) "Hoca reddetti" else "Hocadan onay bekleniyor"
    val teacherName = teachers.find { it.id == request.teacherId }?.let { teacher ->
        listOf(teacher.title, teacher.name, teacher.surname)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }.orEmpty().ifBlank { request.teacherName.ifBlank { "Hoca #${request.teacherId}" } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = statusColor,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(52.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(dayShorts.getOrElse(request.day) { "?" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(timeSlots.getOrElse(request.timeSlot) { "?" }, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${request.courseCode} - ${request.courseName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(teacherName, fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MeetingRoom, null, tint = PrimaryBlue, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(request.classroomId, fontSize = 12.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isRejected && request.teacherNote.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Geri bildirim: ${request.teacherNote}",
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentDialog(
    teachers: List<Teacher>,
    courses: List<Course>,
    classrooms: List<Classroom>,
    days: List<String>,
    timeSlots: List<String>,
    error: String?,
    isAssigning: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Course, Teacher, Classroom, Int, Int) -> Unit
) {
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var selectedTeacher by remember { mutableStateOf<Teacher?>(null) }
    var selectedClassroom by remember { mutableStateOf<Classroom?>(null) }
    var selectedDay by remember { mutableIntStateOf(0) }
    var selectedTimeSlot by remember { mutableIntStateOf(0) }

    var courseExpanded by remember { mutableStateOf(false) }
    var teacherExpanded by remember { mutableStateOf(false) }
    var classroomExpanded by remember { mutableStateOf(false) }
    var timeExpanded by remember { mutableStateOf(false) }

    val uniqueCourses = remember(courses) {
        courses.distinctBy { it.code }
    }

    val filteredTeachers = remember(selectedCourse, teachers, courses) {
        if (selectedCourse == null) {
            emptyList()
        } else {
            val validTeacherIds = courses.filter { it.code == selectedCourse?.code }.mapNotNull { it.teacherId }
            teachers.filter { it.id in validTeacherIds }
        }
    }

    LaunchedEffect(selectedCourse) {
        if (selectedCourse != null) {
            val validTeacherIds = courses.filter { it.code == selectedCourse?.code }.mapNotNull { it.teacherId }
            if (selectedTeacher == null || selectedTeacher?.id !in validTeacherIds) {
                selectedTeacher = if (filteredTeachers.size == 1) filteredTeachers.first() else null
            }
        } else {
            selectedTeacher = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        content = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            ) {
                LazyColumn(modifier = Modifier.padding(24.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Assignment, null, tint = PrimaryBlue)
                            Spacer(Modifier.width(12.dp))
                            Text("Yeni Ders Ataması", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    // Error
                    if (error != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(error, color = ErrorRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    // Course Dropdown
                    item {
                        Text("Ders *", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                        Spacer(Modifier.height(4.dp))
                        ExposedDropdownMenuBox(expanded = courseExpanded, onExpandedChange = { courseExpanded = it }) {
                            OutlinedTextField(
                                value = selectedCourse?.let { "${it.code} - ${it.name}" } ?: "",
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Ders seçin") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = courseExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = courseExpanded, onDismissRequest = { courseExpanded = false }) {
                                uniqueCourses.forEach { course ->
                                    DropdownMenuItem(
                                        text = { Text("${course.code} - ${course.name}", fontSize = 13.sp) },
                                        onClick = { selectedCourse = course; courseExpanded = false }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // Teacher Dropdown
                    item {
                        Text("Hoca *", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                        Spacer(Modifier.height(4.dp))
                        ExposedDropdownMenuBox(expanded = teacherExpanded, onExpandedChange = { teacherExpanded = it }) {
                            OutlinedTextField(
                                value = selectedTeacher?.let { "${it.title} ${it.name} ${it.surname}" } ?: "",
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Hoca seçin") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teacherExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = teacherExpanded, onDismissRequest = { teacherExpanded = false }) {
                                if (filteredTeachers.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Önce ders seçin", color = Color.Gray, fontSize = 13.sp) },
                                        onClick = { teacherExpanded = false }
                                    )
                                } else {
                                    filteredTeachers.forEach { teacher ->
                                        DropdownMenuItem(
                                            text = { Text("${teacher.title} ${teacher.name} ${teacher.surname}", fontSize = 13.sp) },
                                            onClick = { selectedTeacher = teacher; teacherExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // Classroom Dropdown
                    item {
                        Text("Sınıf *", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                        Spacer(Modifier.height(4.dp))
                        ExposedDropdownMenuBox(expanded = classroomExpanded, onExpandedChange = { classroomExpanded = it }) {
                            OutlinedTextField(
                                value = selectedClassroom?.let { "${it.roomCode} (${it.capacity} kişi)" } ?: "",
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Sınıf seçin") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classroomExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = classroomExpanded, onDismissRequest = { classroomExpanded = false }) {
                                classrooms.forEach { classroom ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(classroom.roomCode, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    if (classroom.department.isNotEmpty()) {
                                                        Text(classroom.department, fontSize = 11.sp, color = Color.Gray)
                                                    }
                                                }
                                                Surface(
                                                    color = PrimaryBlue.copy(alpha = 0.1f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        "${classroom.capacity} kişi",
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = PrimaryBlue
                                                    )
                                                }
                                            }
                                        },
                                        onClick = { selectedClassroom = classroom; classroomExpanded = false }
                                    )
                                }
                            }
                        }
                        // Capacity info chip
                        selectedClassroom?.let { cr ->
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                color = SecondaryBlue.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.People, null, tint = SecondaryBlue, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Kapasite: ${cr.capacity} kişi" + if (cr.department.isNotEmpty()) " • ${cr.department}" else "",
                                        fontSize = 11.sp,
                                        color = SecondaryBlue,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // Day selector
                    item {
                        Text("Gün *", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            days.forEachIndexed { index, day ->
                                val isSelected = selectedDay == index
                                Surface(
                                    color = if (isSelected) PrimaryBlue else BackgroundLight,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).clickable { selectedDay = index }
                                ) {
                                    Text(
                                        day.take(3),
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        color = if (isSelected) Color.White else Color.Gray,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // Time slot dropdown
                    item {
                        Text("Saat Dilimi *", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                        Spacer(Modifier.height(4.dp))
                        ExposedDropdownMenuBox(expanded = timeExpanded, onExpandedChange = { timeExpanded = it }) {
                            OutlinedTextField(
                                value = timeSlots[selectedTimeSlot],
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = timeExpanded, onDismissRequest = { timeExpanded = false }) {
                                timeSlots.forEachIndexed { index, time ->
                                    DropdownMenuItem(
                                        text = { Text(time) },
                                        onClick = { selectedTimeSlot = index; timeExpanded = false }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    // Buttons
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) { Text("İPTAL") }
                            Button(
                                onClick = {
                                    if (selectedCourse != null && selectedTeacher != null && selectedClassroom != null && !isAssigning) {
                                        onConfirm(selectedCourse!!, selectedTeacher!!, selectedClassroom!!, selectedDay, selectedTimeSlot)
                                    }
                                },
                                enabled = selectedCourse != null && selectedTeacher != null && selectedClassroom != null && !isAssigning,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isAssigning) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("ATANIYOR", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("ATA", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun AssignmentModeDialog(
    assignment: PendingAssignment,
    days: List<String>,
    timeSlots: List<String>,
    isAssigning: Boolean,
    onDismiss: () -> Unit,
    onSendProposal: () -> Unit,
    onForceAssign: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isAssigning) onDismiss() },
        title = { Text("Atama Yontemi", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${assignment.course.code} - ${assignment.course.name}",
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text("${assignment.teacher.title} ${assignment.teacher.name} ${assignment.teacher.surname}")
                Text("${days.getOrElse(assignment.day) { "Gun" }} / ${timeSlots.getOrElse(assignment.timeSlot) { "Saat" }}")
                Text("Sinif: ${assignment.classroom.roomCode}")
                Surface(
                    color = PrimaryBlue.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Hocaya onay icin gonderirseniz ders kesin programa islenmez; hoca onaylayinca programa duser. Mecburi ata secenegi dersi direkt kesin programa yazar.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDark
                    )
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSendProposal,
                    enabled = !isAssigning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("HOCAYA ONAY ICIN GONDER")
                }
                OutlinedButton(
                    onClick = onForceAssign,
                    enabled = !isAssigning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                ) {
                    Icon(Icons.Default.PriorityHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("MECBURİ OLARAK ATA")
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isAssigning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("IPTAL")
                }
            }
        }
    )
}
