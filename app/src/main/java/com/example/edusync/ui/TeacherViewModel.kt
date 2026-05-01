package com.example.edusync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edusync.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// PDF 3.1: Modelling screen states with a sealed class
sealed class TeacherUiState<out T> {
    object Idle : TeacherUiState<Nothing>()
    object Loading : TeacherUiState<Nothing>()
    data class Success<T>(val data: T) : TeacherUiState<T>()
    data class Error(val message: String, val retryable: Boolean = true) : TeacherUiState<Nothing>()
}

@HiltViewModel
class TeacherViewModel @Inject constructor(
    private val repository: TeacherRepository
) : ViewModel() {

    private val _selectedTeacherId = MutableStateFlow<Int?>(null)
    val selectedTeacherId = _selectedTeacherId.asStateFlow()

    private val _isAdminEditing = MutableStateFlow(false)
    val isAdminEditing = _isAdminEditing.asStateFlow()

    private val _conflictError = MutableStateFlow<String?>(null)
    val conflictError = _conflictError.asStateFlow()

    private val _isAssigning = MutableStateFlow(false)
    val isAssigning = _isAssigning.asStateFlow()

    val classrooms = repository.getAllClassrooms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleEntries = repository.getAllScheduleEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // PDF 3.2: Using StateFlow for predictable UI updates
    val teachers = repository.getAllTeachers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val globalSchedules = repository.getAllAvailabilities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // PDF Optimization: Pre-calculate grouped schedule to avoid heavy loops in UI (GlobalScheduleScreen)
    @OptIn(ExperimentalCoroutinesApi::class)
    val groupedGlobalSchedules = combine(teachers, globalSchedules) { teacherList, scheduleMap ->
        val result = mutableMapOf<String, List<Pair<Teacher, TeacherAvailability>>>()
        scheduleMap.forEach { (teacherId, availabilities) ->
            val teacher = teacherList.find { it.id == teacherId } ?: return@forEach
            availabilities.filter { it.isBusy }.forEach { availability ->
                val key = "${availability.dayIndex}_${availability.slotIndex}"
                val currentList = result.getOrDefault(key, emptyList())
                result[key] = currentList + (teacher to availability)
            }
        }
        result
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentTeacher = _selectedTeacherId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else repository.getTeacherByIdFlow(id)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val availabilityMatrix = combine(_selectedTeacherId, currentTeacher, _isAdminEditing) { id, teacher, adminEditing ->
        Triple(id, teacher, adminEditing)
    }.flatMapLatest { (id, teacher, editing) ->
        if (id == null) return@flatMapLatest flowOf(emptyList<TeacherAvailability>())
        if (editing || teacher?.scheduleStatus == ScheduleStatus.ADMIN_PROPOSAL) {
            repository.getProposal(id)
        } else {
            repository.getAvailability(id)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // PDF Optimization: Availability Map for O(1) lookup in TeacherScheduleScreen
    val availabilityMap = availabilityMatrix.map { list ->
        list.associateBy { "${it.dayIndex}_${it.slotIndex}" }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val teacherCourses = _selectedTeacherId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList<Course>())
        else repository.getCoursesByTeacher(id)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTeacher(id: Int) {
        if (_selectedTeacherId.value != id) {
            _selectedTeacherId.value = id
            _isAdminEditing.value = false
        }
    }

    fun clearConflictError() {
        _conflictError.value = null
    }

    // --- PDF 8: PRODUCTION-QUALITY ERROR HANDLING & MISSING METHODS FOR ACTIVATION ---

    suspend fun checkVerificationCode(code: String): VerificationCode? = withContext(Dispatchers.IO) {
        try {
            repository.getVerificationCode(code)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun getTeacherById(id: Int): Teacher? = withContext(Dispatchers.IO) {
        try {
            repository.getTeacherById(id)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun activateAccount(code: String, username: String, pass: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                repository.activateTeacherAccount(code, User(username = username, password = pass))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
    }
    
    suspend fun activateTeacherAccount(code: String, user: User): Boolean {
        return try {
            // PDF 2.1: IO operations on IO dispatcher
            withContext(Dispatchers.IO) {
                repository.activateTeacherAccount(code, user)
            }
        } catch (e: CancellationException) {
            throw e // PDF 8: ALWAYS re-throw CancellationException
        } catch (e: Exception) {
            false
        }
    }

    // --- ADMIN ACTIONS ---

    fun startAdminEdit() {
        val id = _selectedTeacherId.value ?: return
        viewModelScope.launch {
            try {
                repository.copyAvailabilityToProposal(id)
                _isAdminEditing.value = true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _conflictError.value = "Düzenleme başlatılamadı: ${e.localizedMessage}"
            }
        }
    }

    fun cancelAdminEdit() {
        val id = _selectedTeacherId.value ?: return
        viewModelScope.launch {
            try {
                repository.discardProposal(id)
                _isAdminEditing.value = false
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun assignCourse(dayIndex: Int, slotIndex: Int, course: Course, classroom: String) {
        val teacherId = _selectedTeacherId.value ?: return
        if (_isAssigning.value) return
        _isAssigning.value = true
        
        viewModelScope.launch {
            try {
                // PDF 2.1: Heavy checks on IO/Default
                val conflict = withContext(Dispatchers.IO) {
                    repository.checkScheduleConflict(dayIndex, slotIndex, teacherId, classroom, course.code)
                }
                
                if (conflict != null) {
                    if (conflict == "SAME_EXACT_ASSIGNMENT") {
                        // Allow update, but show a specific info message that it was the existing program
                        _conflictError.value = "Mevcut eski program bu"
                    } else {
                        _conflictError.value = conflict
                        return@launch
                    }
                } else {
                    _conflictError.value = null
                }
                repository.updateProposal(
                    TeacherAvailability(
                        teacherId = teacherId,
                        dayIndex = dayIndex,
                        slotIndex = slotIndex,
                        isBusy = true,
                        courseName = course.name,
                        courseCode = course.code,
                        classroom = classroom
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _conflictError.value = "Hata: ${e.localizedMessage}"
            } finally {
                _isAssigning.value = false
            }
        }
    }

    fun clearSlot(dayIndex: Int, slotIndex: Int) {
        val teacherId = _selectedTeacherId.value ?: return
        viewModelScope.launch {
            try {
                repository.updateProposal(
                    TeacherAvailability(teacherId = teacherId, dayIndex = dayIndex, slotIndex = slotIndex, isBusy = false)
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun submitAdminProposal(note: String) {
        val id = _selectedTeacherId.value ?: return
        viewModelScope.launch {
            try {
                repository.updateScheduleStatus(id, ScheduleStatus.ADMIN_PROPOSAL, adminNote = note, teacherNote = "")
                _isAdminEditing.value = false
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun addTeacher(name: String, surname: String) {
        viewModelScope.launch {
            try {
                repository.insertTeacher(Teacher(name = name, surname = surname))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun deleteTeacher(teacher: Teacher) {
        viewModelScope.launch {
            try {
                repository.deleteTeacher(teacher)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun deleteAllTeachers() {
        viewModelScope.launch {
            try {
                repository.deleteAllTeachers()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    // --- TEACHER ACTIONS ---

    fun approveAdminProposal() {
        val id = _selectedTeacherId.value ?: return
        viewModelScope.launch {
            try {
                repository.applyProposal(id)
                repository.updateScheduleStatus(id, ScheduleStatus.APPROVED, adminNote = "", teacherNote = "")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun rejectAdminProposal(note: String) {
        val id = _selectedTeacherId.value ?: return
        viewModelScope.launch {
            try {
                repository.discardProposal(id)
                repository.updateScheduleStatus(id, ScheduleStatus.REJECTED, adminNote = "", teacherNote = note)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }
}
