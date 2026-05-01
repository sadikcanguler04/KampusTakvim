package com.example.edusync.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edusync.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val excelManager: ExcelManager,
    private val teacherRepository: TeacherRepository,
    private val userRepository: FirebaseUserRepository
) : ViewModel() {

    private val _importState = MutableStateFlow<ImportResult?>(null)
    val importState = _importState.asStateFlow()

    private val _excelPreview = MutableStateFlow<List<ExcelPreviewItem>?>(null)
    val excelPreview = _excelPreview.asStateFlow()

    private var currentUri: Uri? = null

    private val _isResetting = MutableStateFlow(false)
    val isResetting = _isResetting.asStateFlow()

    private val _resetMessage = MutableStateFlow<String?>(null)
    val resetMessage = _resetMessage.asStateFlow()

    // --- Existing Flows ---
    val teachers = teacherRepository.getAllTeachers().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val temporaryCredentials = teacherRepository.getTemporaryCredentials().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // PDF Optimization: Use Dispatchers.Default for heavy mapping and lookup
    val verificationCodes = userRepository.getAllVerificationCodes()
        .combine(teacherRepository.getAllTeachers()) { codes, allTeachers ->
            val teacherMap = allTeachers.associateBy { it.id }
            codes.map { code ->
                val teacher = teacherMap[code.teacherId]
                code.copy(createdBy = teacher?.let { "${it.title} ${it.name} ${it.surname}" } ?: "Genel Kod")
            }
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ========== Phase 2: Classroom Management ==========

    val classrooms = teacherRepository.getAllClassrooms().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _classroomError = MutableStateFlow<String?>(null)
    val classroomError = _classroomError.asStateFlow()

    fun addClassroom(roomCode: String, capacity: Int, department: String) {
        viewModelScope.launch {
            try {
                teacherRepository.insertClassroom(
                    Classroom(roomCode = roomCode, capacity = capacity, department = department)
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _classroomError.value = "Sınıf eklenemedi: ${e.localizedMessage}"
            }
        }
    }

    fun deleteClassroom(classroomId: String) {
        viewModelScope.launch {
            try {
                teacherRepository.deleteClassroom(classroomId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun clearClassroomError() { _classroomError.value = null }

    // ========== Classroom Excel Import ==========

    private val _classroomImportState = MutableStateFlow<ImportResult?>(null)
    val classroomImportState = _classroomImportState.asStateFlow()

    private val _classroomPreview = MutableStateFlow<List<ClassroomPreviewItem>?>(null)
    val classroomPreview = _classroomPreview.asStateFlow()

    private var classroomUri: Uri? = null

    fun loadClassroomPreview(context: Context, uri: Uri) {
        classroomUri = uri
        viewModelScope.launch {
            val result = excelManager.getClassroomPreview(context, uri)
            if (result.isSuccess) {
                _classroomPreview.value = result.getOrNull()
            } else {
                _classroomImportState.value = ImportResult.Error(result.exceptionOrNull()?.message ?: "Önizleme yüklenemedi")
            }
        }
    }

    fun confirmClassroomImport(context: Context) {
        val uri = classroomUri ?: return
        viewModelScope.launch {
            _classroomImportState.value = ImportResult.Loading
            val result = excelManager.importClassrooms(context, uri)
            _classroomImportState.value = if (result.isSuccess) {
                ImportResult.Success(result.getOrNull() ?: 0)
            } else {
                ImportResult.Error(result.exceptionOrNull()?.message ?: "Aktarım hatası")
            }
            _classroomPreview.value = null
        }
    }

    fun clearClassroomImportState() {
        _classroomImportState.value = null
        _classroomPreview.value = null
        classroomUri = null
    }

    // ========== Phase 2: Schedule Entry (Assignment) Management ==========

    val allCourses = teacherRepository.getAllCourses().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val scheduleEntries = teacherRepository.getAllScheduleEntries().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _assignmentError = MutableStateFlow<String?>(null)
    val assignmentError = _assignmentError.asStateFlow()

    private val _assignmentSuccess = MutableStateFlow(false)
    val assignmentSuccess = _assignmentSuccess.asStateFlow()

    private val _isAssigning = MutableStateFlow(false)
    val isAssigning = _isAssigning.asStateFlow()

    fun assignSchedule(
        courseCode: String,
        courseName: String,
        teacherId: Int,
        classroomId: String,
        day: Int,
        timeSlot: Int
    ) {
        if (_isAssigning.value) return
        _isAssigning.value = true
        _assignmentError.value = null

        viewModelScope.launch {
            try {
                // Phase 2 Madde 5.2: Double-booking prevention
                val conflict = teacherRepository.checkScheduleConflict(day, timeSlot, teacherId, classroomId, courseCode)
                if (conflict != null) {
                    _assignmentError.value = conflict
                    return@launch
                }

                // 1) Insert into schedule_entries (new Phase 2 table)
                teacherRepository.insertScheduleEntry(
                    ScheduleEntry(
                        courseCode = courseCode,
                        courseName = courseName,
                        teacherId = teacherId,
                        classroomId = classroomId,
                        day = day,
                        timeSlot = timeSlot
                    )
                )

                // 2) SYNC: Also write to the old availability table so
                //    TeacherScheduleScreen & GlobalScheduleScreen display the data
                teacherRepository.syncScheduleToAvailability(
                    TeacherAvailability(
                        teacherId = teacherId,
                        dayIndex = day,
                        slotIndex = timeSlot,
                        isBusy = true,
                        courseName = courseName,
                        courseCode = courseCode,
                        classroom = classroomId
                    )
                )

                _assignmentError.value = null
                _assignmentSuccess.value = true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _assignmentError.value = "Atama yapılamadı: ${e.localizedMessage}"
            } finally {
                _isAssigning.value = false
            }
        }
    }

    fun deleteScheduleEntry(entryId: String) {
        viewModelScope.launch {
            try {
                // Find the entry first so we can also remove from availability
                val entry = scheduleEntries.value.find { it.id == entryId }
                teacherRepository.deleteScheduleEntry(entryId)
                // SYNC: Also clear from the old availability table
                if (entry != null) {
                    teacherRepository.syncScheduleToAvailability(
                        TeacherAvailability(
                            teacherId = entry.teacherId,
                            dayIndex = entry.day,
                            slotIndex = entry.timeSlot,
                            isBusy = false
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun clearAssignmentError() { _assignmentError.value = null }
    fun clearAssignmentSuccess() { _assignmentSuccess.value = false }

    // ========== Phase 2 Madde 6: Admin Dashboard Summary Panels ==========

    val unassignedTeachers: StateFlow<List<Teacher>> = combine(teachers, scheduleEntries) { teacherList, entries ->
        val assignedTeacherIds = entries.map { it.teacherId }.toSet()
        teacherList.filter { it.id !in assignedTeacherIds }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unassignedCourses: StateFlow<List<Course>> = combine(allCourses, scheduleEntries) { courseList, entries ->
        val assignedCourseCodes = entries.map { it.courseCode }.toSet()
        courseList.filter { it.code !in assignedCourseCodes }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableClassrooms: StateFlow<List<Pair<Classroom, Int>>> = combine(classrooms, scheduleEntries) { classroomList, entries ->
        val totalSlots = 5 * 9 // 5 gün × 9 saat dilimi = 45 slot
        classroomList.map { classroom ->
            val usedSlots = entries.count { it.classroomId == classroom.roomCode }
            val freeSlots = totalSlots - usedSlots
            classroom to freeSlots
        }.filter { it.second > 0 }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ========== Existing functions ==========

    fun generateCodeForTeacher(teacherId: Int) {
        viewModelScope.launch {
            teacherRepository.generateCodeForTeacher(teacherId)
        }
    }

    fun loadPreview(context: Context, uri: Uri) {
        currentUri = uri
        viewModelScope.launch {
            val result = excelManager.getPreview(context, uri)
            if (result.isSuccess) {
                _excelPreview.value = result.getOrNull()
            } else {
                _importState.value = ImportResult.Error(result.exceptionOrNull()?.message ?: "Önizleme yüklenemedi")
            }
        }
    }

    fun confirmImport(context: Context) {
        val uri = currentUri ?: return
        viewModelScope.launch {
            _importState.value = ImportResult.Loading
            val result = excelManager.importExcel(context, uri)
            _importState.value = if (result.isSuccess) {
                val report = result.getOrNull() ?: ExcelImportReport()
                ImportResult.Success(report.count, report.generatedCredentials)
            } else {
                ImportResult.Error(result.exceptionOrNull()?.message ?: "Aktarım hatası")
            }
            _excelPreview.value = null
        }
    }

    fun clearImportState() {
        _importState.value = null
        _excelPreview.value = null
        currentUri = null
    }

    fun resetApplicationData() {
        if (_isResetting.value) return
        viewModelScope.launch {
            _isResetting.value = true
            try {
                teacherRepository.resetApplicationData()
                _resetMessage.value = "Sistem fabrika ayarlarina donduruldu. Admin hesabi korundu."
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _resetMessage.value = "Sifirlama basarisiz: ${e.localizedMessage}"
            } finally {
                _isResetting.value = false
            }
        }
    }

    fun clearResetMessage() {
        _resetMessage.value = null
    }
}
