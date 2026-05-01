package com.example.edusync.data

import com.google.firebase.database.*
import com.example.edusync.util.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TeacherRepository @Inject constructor(
    private val database: FirebaseDatabase
) {
    private val teachersRef = database.getReference("teachers")
    private val coursesRef = database.getReference("courses")
    private val availabilityRef = database.getReference("availability")
    private val proposalsRef = database.getReference("proposals")
    private val codesRef = database.getReference("verification_codes")
    private val usersRef = database.getReference("users")
    private val departmentsRef = database.getReference("departments")
    private val temporaryCredentialsRef = database.getReference("temporary_credentials")

    private fun getTeacherKeyById(id: Int): String = id.toString()

    fun getAllTeachers(): Flow<List<Teacher>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch(Dispatchers.Default) {
                    val list = snapshot.children.mapNotNull { it.getValue(Teacher::class.java) }
                    val decryptedList = list.map { t ->
                        t.copy(
                            name = SecurityUtils.decrypt(t.name),
                            surname = SecurityUtils.decrypt(t.surname),
                            department = SecurityUtils.decrypt(t.department),
                            title = SecurityUtils.decrypt(t.title),
                            adminNote = SecurityUtils.decrypt(t.adminNote),
                            teacherNote = SecurityUtils.decrypt(t.teacherNote)
                        )
                    }
                    trySend(decryptedList)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        // PDF Optimization: Add listener without wrapping in a launch block to prevent memory leak and cancellation mismatch
        teachersRef.addValueEventListener(listener)
        awaitClose { 
            teachersRef.removeEventListener(listener) 
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun getOrInsertTeacherOptimized(teacher: Teacher, existingTeachers: List<Teacher>): Long = withContext(Dispatchers.IO) {
        val normalizedInputName = withContext(Dispatchers.Default) { normalize(teacher.name) }
        val normalizedInputSurname = withContext(Dispatchers.Default) { normalize(teacher.surname) }
        
        val existing = existingTeachers.find { 
            normalize(it.name) == normalizedInputName && normalize(it.surname) == normalizedInputSurname 
        }
        
        if (existing != null) return@withContext existing.id.toLong()

        val id = System.currentTimeMillis().toInt()
        val departmentId = teacher.departmentId.ifBlank {
            if (teacher.department.isNotBlank()) ensureDepartment(teacher.department) else ""
        }
        val targetRef = teachersRef.child(id.toString())
        
        val encryptedTeacher = withContext(Dispatchers.Default) {
            teacher.copy(
                id = id,
                name = SecurityUtils.encrypt(teacher.name),
                surname = SecurityUtils.encrypt(teacher.surname),
                department = SecurityUtils.encrypt(teacher.department),
                departmentId = departmentId,
                title = SecurityUtils.encrypt(teacher.title)
            )
        }
        targetRef.setValue(encryptedTeacher).await()
        id.toLong()
    }

    fun getTeacherByIdFlow(id: Int): Flow<Teacher?> = callbackFlow {
        val ref = teachersRef.child(id.toString())
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch(Dispatchers.Default) {
                    val teacher = snapshot.getValue(Teacher::class.java)
                    val decrypted = teacher?.let { t ->
                        t.copy(
                            name = SecurityUtils.decrypt(t.name),
                            surname = SecurityUtils.decrypt(t.surname),
                            department = SecurityUtils.decrypt(t.department),
                            title = SecurityUtils.decrypt(t.title),
                            adminNote = SecurityUtils.decrypt(t.adminNote),
                            teacherNote = SecurityUtils.decrypt(t.teacherNote)
                        )
                    }
                    trySend(decrypted)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { 
            ref.removeEventListener(listener) 
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun getTeacherById(id: Int): Teacher? = withContext(Dispatchers.IO) {
        val snapshot = teachersRef.child(id.toString()).get().await()
        snapshot.getValue(Teacher::class.java)?.let { t ->
            withContext(Dispatchers.Default) {
                t.copy(
                    name = SecurityUtils.decrypt(t.name),
                    surname = SecurityUtils.decrypt(t.surname),
                    department = SecurityUtils.decrypt(t.department),
                    title = SecurityUtils.decrypt(t.title),
                    adminNote = SecurityUtils.decrypt(t.adminNote),
                    teacherNote = SecurityUtils.decrypt(t.teacherNote)
                )
            }
        }
    }

    suspend fun insertTeacher(teacher: Teacher): Long = withContext(Dispatchers.IO) {
        val all = getAllTeachers().first()
        val existing = all.find { 
            normalize(it.name) == normalize(teacher.name) && normalize(it.surname) == normalize(teacher.surname) 
        }
        if (existing != null) return@withContext existing.id.toLong()

        val id = System.currentTimeMillis().toInt()
        val departmentId = teacher.departmentId.ifBlank {
            if (teacher.department.isNotBlank()) ensureDepartment(teacher.department) else ""
        }
        val targetRef = teachersRef.child(id.toString())
        
        val encryptedTeacher = withContext(Dispatchers.Default) {
            teacher.copy(
                id = id,
                name = SecurityUtils.encrypt(teacher.name),
                surname = SecurityUtils.encrypt(teacher.surname),
                department = SecurityUtils.encrypt(teacher.department),
                departmentId = departmentId,
                title = SecurityUtils.encrypt(teacher.title)
            )
        }
        targetRef.setValue(encryptedTeacher).await()
        id.toLong()
    }

    suspend fun updateScheduleStatus(teacherId: Int, status: ScheduleStatus, adminNote: String? = null, teacherNote: String? = null) = withContext(Dispatchers.IO) {
        val key = getTeacherKeyById(teacherId)
        val updates = mutableMapOf<String, Any>("scheduleStatus" to status.name)
        
        withContext(Dispatchers.Default) {
            adminNote?.let { updates["adminNote"] = if (it.isEmpty()) "" else SecurityUtils.encrypt(it) }
            teacherNote?.let { updates["teacherNote"] = if (it.isEmpty()) "" else SecurityUtils.encrypt(it) }
        }
        
        teachersRef.child(key).updateChildren(updates).await()
    }

    private fun normalize(t: String) = t.lowercase(Locale("tr")).replace('ı','i').replace('ş','s').replace('ğ','g').replace('ü','u').replace('ö','o').replace('ç','c').trim()

    private fun normalizeForKey(t: String) = t.lowercase(Locale("tr"))
        .replace('\u0131', 'i')
        .replace('\u015f', 's')
        .replace('\u011f', 'g')
        .replace('\u00fc', 'u')
        .replace('\u00f6', 'o')
        .replace('\u00e7', 'c')
        .trim()

    private fun departmentKey(name: String): String {
        val normalized = normalizeForKey(name)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return normalized.ifBlank { "genel" }
    }

    suspend fun ensureDepartment(departmentName: String): String = withContext(Dispatchers.IO) {
        val name = departmentName.ifBlank { "Genel" }
        val id = departmentKey(name)
        val snapshot = departmentsRef.child(id).get().await()
        if (!snapshot.exists()) {
            departmentsRef.child(id).setValue(Department(id = id, name = name)).await()
        }
        id
    }

    fun getAllDepartments(): Flow<List<Department>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch(Dispatchers.Default) {
                    val list = snapshot.children.mapNotNull { it.getValue(Department::class.java) }
                    trySend(list)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        departmentsRef.addValueEventListener(listener)
        awaitClose { departmentsRef.removeEventListener(listener) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    fun getTemporaryCredentials(): Flow<List<TemporaryCredential>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch(Dispatchers.Default) {
                    val list = snapshot.children
                        .mapNotNull { it.getValue(TemporaryCredential::class.java) }
                        .map { credential ->
                            credential.copy(
                                temporaryPassword = if (credential.consumed) {
                                    ""
                                } else {
                                    SecurityUtils.decrypt(credential.temporaryPassword)
                                }
                            )
                        }
                        .sortedWith(compareBy<TemporaryCredential> { it.consumed }.thenByDescending { it.createdAt })
                    trySend(list)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        temporaryCredentialsRef.addValueEventListener(listener)
        awaitClose { temporaryCredentialsRef.removeEventListener(listener) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun createInitialTeacherAccountIfMissing(
        teacher: Teacher,
        baseUsername: String,
        initialPassword: String
    ): GeneratedCredential? = withContext(Dispatchers.IO) {
        val existingUsers = usersRef.get().await().children.mapNotNull { it.getValue(User::class.java) }
        if (existingUsers.any { it.teacherId == teacher.id }) return@withContext null

        val cleanBase = baseUsername.ifBlank { "hoca_${teacher.id}" }
        var username = cleanBase
        var suffix = 2
        while (existingUsers.any { it.username == username }) {
            username = "${cleanBase}${suffix++}"
        }

        val hashedPassword = withContext(Dispatchers.Default) { SecurityUtils.hashPassword(initialPassword) }
        val user = User(
            username = username,
            password = hashedPassword,
            role = UserRole.TEACHER,
            teacherId = teacher.id,
            mustChangePassword = true
        )
        usersRef.child(username).setValue(user).await()
        val teacherName = listOf(teacher.title, teacher.name, teacher.surname)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val temporaryCredential = TemporaryCredential(
            teacherId = teacher.id,
            teacherName = teacherName,
            username = username,
            temporaryPassword = SecurityUtils.encrypt(initialPassword),
            createdAt = System.currentTimeMillis(),
            consumed = false,
            consumedAt = 0L
        )
        temporaryCredentialsRef.child(teacher.id.toString()).setValue(temporaryCredential).await()

        GeneratedCredential(
            teacherName = teacherName,
            username = username,
            initialPassword = initialPassword
        )
    }

    suspend fun generateCodeForTeacher(teacherId: Int): String = withContext(Dispatchers.IO) {
        val code = (100000..999999).random().toString()
        codesRef.child(code).setValue(VerificationCode(code = code, teacherId = teacherId, isUsed = false)).await()
        code
    }

    suspend fun getVerificationCode(code: String): VerificationCode? = withContext(Dispatchers.IO) {
        val snap = codesRef.child(code).get().await()
        snap.getValue(VerificationCode::class.java)
    }

    suspend fun activateTeacherAccount(code: String, user: User): Boolean = withContext(Dispatchers.IO) {
        val hashedPassword = withContext(Dispatchers.Default) { SecurityUtils.hashPassword(user.password) }

        // Mapped to Task 4: Race Condition fixed with Firebase Transaction
        val teacherId = kotlinx.coroutines.suspendCancellableCoroutine<Int?> { continuation ->
            var isResumed = false
            codesRef.child(code).runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val vCode = currentData.getValue(VerificationCode::class.java)
                    if (vCode == null) {
                        // Cache miss might cause this to be null. Return success to force a server sync.
                        return Transaction.success(currentData)
                    }
                    if (vCode.isUsed || vCode.teacherId == null) {
                        return Transaction.abort()
                    }
                    vCode.isUsed = true
                    currentData.value = vCode
                    return Transaction.success(currentData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (!isResumed) {
                        isResumed = true
                        if (committed && error == null) {
                            val vCode = snapshot?.getValue(VerificationCode::class.java)
                            if (vCode == null || vCode.teacherId == null || !vCode.isUsed) {
                                continuation.resumeWith(Result.success(null))
                            } else {
                                continuation.resumeWith(Result.success(vCode.teacherId))
                            }
                        } else {
                            continuation.resumeWith(Result.success(null))
                        }
                    }
                }
            })
        }

        if (teacherId == null) return@withContext false

        val newUser = user.copy(
            role = UserRole.TEACHER,
            teacherId = teacherId,
            password = hashedPassword,
            mustChangePassword = false
        )
        usersRef.child(newUser.username).setValue(newUser).await()
        true
    }

    fun getAvailability(teacherId: Int): Flow<List<TeacherAvailability>> = callbackFlow {
        val ref = availabilityRef.child(teacherId.toString())
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch(Dispatchers.Default) {
                    val list = snapshot.children.mapNotNull { it.getValue(TeacherAvailability::class.java) }
                    val decryptedList = list.map { a -> 
                        a.copy(courseName = SecurityUtils.decrypt(a.courseName)) 
                    }
                    trySend(decryptedList)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { 
            ref.removeEventListener(listener) 
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    fun getProposal(teacherId: Int): Flow<List<TeacherAvailability>> = callbackFlow {
        val ref = proposalsRef.child(teacherId.toString())
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch(Dispatchers.Default) {
                    val list = snapshot.children.mapNotNull { it.getValue(TeacherAvailability::class.java) }
                    val decryptedList = list.map { a -> 
                        a.copy(courseName = SecurityUtils.decrypt(a.courseName)) 
                    }
                    trySend(decryptedList)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { 
            ref.removeEventListener(listener) 
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun updateProposal(availability: TeacherAvailability) = withContext(Dispatchers.IO) {
        val key = "${availability.dayIndex}_${availability.slotIndex}"
        proposalsRef.child(availability.teacherId.toString()).child(key).setValue(availability).await()
    }

    suspend fun applyProposal(teacherId: Int) = withContext(Dispatchers.IO) {
        val snapshot = proposalsRef.child(teacherId.toString()).get().await()
        
        // 1. Sync to legacy availability table
        availabilityRef.child(teacherId.toString()).setValue(snapshot.value).await()
        
        // 2. Sync to Phase 2 schedule_entries table
        val seSnapshot = scheduleEntriesRef.get().await()
        val updates = mutableMapOf<String, Any?>()
        
        // Delete all old schedule entries for this teacher
        seSnapshot.children.forEach { child ->
            val entry = child.getValue(ScheduleEntry::class.java)
            if (entry?.teacherId == teacherId) {
                updates[child.key!!] = null
            }
        }
        
        // Insert new schedule entries from the approved proposal
        snapshot.children.forEach { daySlotSnap ->
            val avail = daySlotSnap.getValue(TeacherAvailability::class.java)
            if (avail != null && avail.isBusy) {
                // Generate a unique ID for the global schedule entry
                val newId = "${avail.dayIndex}_${avail.slotIndex}_${avail.classroom}_${System.currentTimeMillis()}"
                
                // Keep the raw courseName as encryption is handled during reads/writes in specific layers
                val newEntry = ScheduleEntry(
                    id = newId,
                    courseCode = avail.courseCode,
                    courseName = avail.courseName, // Raw (already encrypted in proposal)
                    teacherId = teacherId,
                    classroomId = avail.classroom,
                    day = avail.dayIndex,
                    timeSlot = avail.slotIndex
                )
                updates[newId] = newEntry
            }
        }
        
        if (updates.isNotEmpty()) {
            scheduleEntriesRef.updateChildren(updates).await()
        }

        // 3. Clear the proposal
        proposalsRef.child(teacherId.toString()).removeValue().await()
    }

    suspend fun discardProposal(teacherId: Int) = withContext(Dispatchers.IO) {
        proposalsRef.child(teacherId.toString()).removeValue().await()
    }

    suspend fun copyAvailabilityToProposal(teacherId: Int) = withContext(Dispatchers.IO) {
        val snapshot = availabilityRef.child(teacherId.toString()).get().await()
        proposalsRef.child(teacherId.toString()).setValue(snapshot.value).await()
    }

    suspend fun checkClassroomConflict(dayIndex: Int, slotIndex: Int, classroom: String, excludeTeacherId: Int): String? = withContext(Dispatchers.IO) {
        // PDF Optimization Task 5: Parallel Process with async/await
        val availabilitiesDeferred = async<DataSnapshot> { availabilityRef.get().await() }
        val teachersDeferred = async<DataSnapshot> { teachersRef.get().await() }
        
        val availabilitiesSnapshot = availabilitiesDeferred.await()
        val teachersSnapshot = teachersDeferred.await()
        
        val teachersMap = withContext(Dispatchers.Default) {
            teachersSnapshot.children.mapNotNull { it.getValue(Teacher::class.java) }.associateBy { it.id }
        }

        for (teacherSnap in availabilitiesSnapshot.children) {
            val tId = teacherSnap.key?.toIntOrNull() ?: continue
            if (tId == excludeTeacherId) continue
            
            val slot = teacherSnap.child("${dayIndex}_${slotIndex}").getValue(TeacherAvailability::class.java)
            if (slot?.isBusy == true && slot.classroom == classroom) {
                val tData = teachersMap[tId]
                return@withContext withContext(Dispatchers.Default) {
                    val name = SecurityUtils.decrypt(tData?.name)
                    val surname = SecurityUtils.decrypt(tData?.surname)
                    val decryptedCourse = SecurityUtils.decrypt(slot.courseName)
                    "$name $surname hocanın dersi var. ($decryptedCourse)"
                }
            }
        }
        null
    }

    fun getAllAvailabilities(): Flow<Map<Int, List<TeacherAvailability>>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch(Dispatchers.Default) {
                    val resultMap = mutableMapOf<Int, List<TeacherAvailability>>()
                    snapshot.children.forEach { teacherSnap ->
                        val tid = teacherSnap.key?.toIntOrNull() ?: return@forEach
                        resultMap[tid] = teacherSnap.children.mapNotNull { 
                            val slot = teacherSnap.child(it.key!!).getValue(TeacherAvailability::class.java)
                            slot?.copy(courseName = SecurityUtils.decrypt(slot.courseName))
                        }
                    }
                    trySend(resultMap)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        availabilityRef.addValueEventListener(listener)
        awaitClose { 
            availabilityRef.removeEventListener(listener) 
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun deleteTeacher(teacher: Teacher) = withContext(Dispatchers.IO) {
        val key = getTeacherKeyById(teacher.id)
        teachersRef.child(key).removeValue().await()
        availabilityRef.child(teacher.id.toString()).removeValue().await()
        proposalsRef.child(teacher.id.toString()).removeValue().await()
    }

    suspend fun insertCourse(course: Course) = withContext(Dispatchers.IO) {
        val id = course.id.ifBlank { "${course.code}_${course.teacherId ?: "general"}" }
        val encryptedName = withContext(Dispatchers.Default) { SecurityUtils.encrypt(course.name) }
        val encryptedCourse = course.copy(id = id, name = encryptedName)
        coursesRef.child(id).setValue(encryptedCourse).await()
    }

    suspend fun insertCoursesBatch(courses: List<Course>) = withContext(Dispatchers.IO) {
        val updates = mutableMapOf<String, Any>()
        withContext(Dispatchers.Default) {
            courses.forEach { course ->
                val encryptedName = SecurityUtils.encrypt(course.name)
                val key = course.id.ifBlank { "${course.code}_${course.teacherId ?: "general"}" }
                updates[key] = course.copy(id = key, name = encryptedName)
            }
        }
        if (updates.isNotEmpty()) {
            coursesRef.updateChildren(updates).await()
        }
    }

    fun getCoursesByTeacher(teacherId: Int): Flow<List<Course>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch(Dispatchers.Default) {
                    val list = snapshot.children.mapNotNull { it.getValue(Course::class.java) }
                    val filteredAndDecrypted = list.filter { it.teacherId == teacherId }.map { c ->
                        c.copy(name = SecurityUtils.decrypt(c.name))
                    }
                    trySend(filteredAndDecrypted)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        coursesRef.addValueEventListener(listener)
        awaitClose { 
            coursesRef.removeEventListener(listener) 
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    // ========== Phase 2: All Courses (no filter) ==========

    fun getAllCourses(): Flow<List<Course>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch(Dispatchers.Default) {
                    val list = snapshot.children.mapNotNull { it.getValue(Course::class.java) }
                    val decryptedList = list.map { c ->
                        c.copy(name = SecurityUtils.decrypt(c.name))
                    }
                    trySend(decryptedList)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        coursesRef.addValueEventListener(listener)
        awaitClose { coursesRef.removeEventListener(listener) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    // ========== Phase 2: Classroom Management ==========

    private val classroomsRef = database.getReference("classrooms")

    fun getAllClassrooms(): Flow<List<Classroom>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch(Dispatchers.Default) {
                    val list = snapshot.children.mapNotNull { it.getValue(Classroom::class.java) }
                    trySend(list)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        classroomsRef.addValueEventListener(listener)
        awaitClose { classroomsRef.removeEventListener(listener) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun insertClassroom(classroom: Classroom) = withContext(Dispatchers.IO) {
        val id = classroom.roomCode.ifEmpty { System.currentTimeMillis().toString() }
        val departmentId = classroom.departmentId.ifBlank {
            if (classroom.department.isNotBlank()) ensureDepartment(classroom.department) else ""
        }
        val entry = classroom.copy(id = id, departmentId = departmentId)
        classroomsRef.child(id).setValue(entry).await()
    }

    suspend fun deleteClassroom(classroomId: String) = withContext(Dispatchers.IO) {
        classroomsRef.child(classroomId).removeValue().await()
    }

    suspend fun insertClassroomsBatch(classrooms: List<Classroom>) = withContext(Dispatchers.IO) {
        val departmentIds = classrooms
            .map { it.department }
            .filter { it.isNotBlank() }
            .distinct()
            .associateWith { ensureDepartment(it) }
        val updates = mutableMapOf<String, Any>()
        withContext(Dispatchers.Default) {
            classrooms.forEach { classroom ->
                val id = classroom.roomCode.ifEmpty { System.currentTimeMillis().toString() }
                val departmentId = classroom.departmentId.ifBlank { departmentIds[classroom.department].orEmpty() }
                updates[id] = classroom.copy(id = id, departmentId = departmentId)
            }
        }
        if (updates.isNotEmpty()) {
            classroomsRef.updateChildren(updates).await()
        }
    }

    // ========== Phase 2: Schedule Entry (Assignment) Management ==========

    private val scheduleEntriesRef = database.getReference("schedule_entries")

    fun getAllScheduleEntries(): Flow<List<ScheduleEntry>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch(Dispatchers.Default) {
                    val list = snapshot.children.mapNotNull { it.getValue(ScheduleEntry::class.java) }
                    val decryptedList = list.map { s ->
                        s.copy(courseName = SecurityUtils.decrypt(s.courseName))
                    }
                    trySend(decryptedList)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        scheduleEntriesRef.addValueEventListener(listener)
        awaitClose { scheduleEntriesRef.removeEventListener(listener) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /**
     * Phase 2 Madde 5.2: Double-booking prevention.
     * Checks BOTH schedule_entries (new) AND availability (legacy) tables.
     * Returns an error message if the same classroom or teacher is already booked at that slot.
     * Returns null if no conflict exists.
     */
    suspend fun checkScheduleConflict(day: Int, timeSlot: Int, teacherId: Int, classroomId: String, courseCode: String): String? = withContext(Dispatchers.IO) {
        // Check 1: schedule_entries table (new system)
        val seSnapshot = scheduleEntriesRef.get().await()
        val entries = withContext(Dispatchers.Default) {
            seSnapshot.children.mapNotNull { it.getValue(ScheduleEntry::class.java) }
        }

        for (entry in entries) {
            if (entry.day == day && entry.timeSlot == timeSlot) {
                val decryptedCourseName = SecurityUtils.decrypt(entry.courseName)
                
                // If it's the exact same teacher, classroom, and course
                if (entry.teacherId == teacherId && entry.classroomId == classroomId && entry.courseCode == courseCode) {
                    return@withContext "SAME_EXACT_ASSIGNMENT"
                }
                
                // Otherwise, check for real conflicts
                if (entry.classroomId == classroomId) {
                    return@withContext "Bu sınıf (${classroomId}) o saatte zaten dolu! ($decryptedCourseName)"
                }
                if (entry.teacherId == teacherId) {
                    return@withContext "Bu hoca o saatte zaten başka bir derse atanmış! ($decryptedCourseName)"
                }
            }
        }

        // Check 2: availability table (legacy system) — also prevents cross-system conflicts
        val availSnapshot = availabilityRef.get().await()
        for (teacherSnap in availSnapshot.children) {
            val tId = teacherSnap.key?.toIntOrNull() ?: continue
            val slotKey = "${day}_${timeSlot}"
            val slot = teacherSnap.child(slotKey).getValue(TeacherAvailability::class.java)
            if (slot?.isBusy == true) {
                val decryptedCourseName = SecurityUtils.decrypt(slot.courseName)
                
                // If it's the exact same teacher, classroom, and course
                if (tId == teacherId && slot.classroom == classroomId && slot.courseCode == courseCode) {
                    return@withContext "SAME_EXACT_ASSIGNMENT"
                }

                if (slot.classroom == classroomId && tId != teacherId) {
                    return@withContext "Bu sınıf (${classroomId}) o saatte zaten dolu! ($decryptedCourseName - mevcut atama)"
                }
                if (tId == teacherId) {
                    return@withContext "Bu hoca o saatte zaten başka bir derse atanmış! ($decryptedCourseName - mevcut atama)"
                }
            }
        }

        null
    }

    suspend fun insertScheduleEntry(entry: ScheduleEntry) = withContext(Dispatchers.IO) {
        val id = entry.id.ifEmpty { "${entry.day}_${entry.timeSlot}_${entry.classroomId}_${System.currentTimeMillis()}" }
        val newEntry = entry.copy(id = id)
        scheduleEntriesRef.child(id).setValue(newEntry).await()
    }

    suspend fun deleteScheduleEntry(entryId: String) = withContext(Dispatchers.IO) {
        scheduleEntriesRef.child(entryId).removeValue().await()
    }

    /**
     * Phase 2: Sync bridge between schedule_entries (new) and availability (legacy).
     * When admin creates/deletes an assignment via AssignmentScreen, this function
     * mirrors the change into the old availability table so TeacherScheduleScreen
     * and GlobalScheduleScreen display the data correctly.
     */
    suspend fun syncScheduleToAvailability(availability: TeacherAvailability) = withContext(Dispatchers.IO) {
        val key = "${availability.dayIndex}_${availability.slotIndex}"
        availabilityRef.child(availability.teacherId.toString()).child(key).setValue(availability).await()
    }
}
