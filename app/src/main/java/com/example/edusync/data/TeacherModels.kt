package com.example.edusync.data

import com.google.firebase.database.PropertyName
import androidx.compose.runtime.Stable

enum class UserRole { ADMIN, TEACHER }

enum class ScheduleStatus { PENDING, APPROVED, REJECTED, ADMIN_PROPOSAL }

enum class AssignmentRequestStatus { PENDING, APPROVED, REJECTED, SUPERSEDED }

@Stable
data class User(
    var id: Int = 0,
    var username: String = "",
    var password: String = "",
    var role: UserRole = UserRole.TEACHER,
    var teacherId: Int? = null,
    var mustChangePassword: Boolean = false
)

@Stable
data class Teacher(
    var id: Int = 0,
    var name: String = "",
    var surname: String = "",
    var department: String = "",
    var departmentId: String = "",
    var title: String = "",
    var scheduleStatus: ScheduleStatus = ScheduleStatus.APPROVED,
    var adminNote: String = "",
    var teacherNote: String = ""
)

@Stable
data class TeacherAvailability(
    var teacherId: Int = 0,
    var dayIndex: Int = 0,
    var slotIndex: Int = 0,
    @get:PropertyName("busy")
    @set:PropertyName("busy")
    @PropertyName("busy")
    var isBusy: Boolean = false,
    var courseName: String = "",
    var courseCode: String = "",
    var classroom: String = ""
)

@Stable
data class Course(
    var id: String = "",
    var code: String = "",
    var name: String = "",
    var departmentId: String = "",
    var teacherId: Int? = null
)

@Stable
data class VerificationCode(
    var code: String = "",
    var teacherId: Int? = null, // Hangi hoca için üretildi?
    @get:PropertyName("used")
    @set:PropertyName("used")
    @PropertyName("used")
    var isUsed: Boolean = false,
    var createdBy: String = "ADMIN"
)

@Stable
data class Classroom(
    var id: String = "",
    var roomCode: String = "",
    var capacity: Int = 0,
    var department: String = "",
    var departmentId: String = ""
)

@Stable
data class ScheduleEntry(
    var id: String = "",
    var courseCode: String = "",
    var courseName: String = "",
    var teacherId: Int = 0,
    var classroomId: String = "",
    var day: Int = 0,
    var timeSlot: Int = 0
)

@Stable
data class AssignmentRequest(
    var id: String = "",
    var teacherId: Int = 0,
    var teacherName: String = "",
    var courseCode: String = "",
    var courseName: String = "",
    var classroomId: String = "",
    var day: Int = 0,
    var timeSlot: Int = 0,
    var status: AssignmentRequestStatus = AssignmentRequestStatus.PENDING,
    var adminNote: String = "",
    var teacherNote: String = "",
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L
)

@Stable
data class Department(
    var id: String = "",
    var name: String = ""
)

@Stable
data class GeneratedCredential(
    val teacherName: String = "",
    val username: String = "",
    val initialPassword: String = ""
)

@Stable
data class TemporaryCredential(
    var teacherId: Int = 0,
    var teacherName: String = "",
    var username: String = "",
    var temporaryPassword: String = "",
    var createdAt: Long = 0L,
    var consumed: Boolean = false,
    var consumedAt: Long = 0L
)

@Stable
data class ExcelImportReport(
    val count: Int = 0,
    val generatedCredentials: List<GeneratedCredential> = emptyList()
)
