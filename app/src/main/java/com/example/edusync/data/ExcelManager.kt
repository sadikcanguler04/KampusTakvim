package com.example.edusync.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.security.SecureRandom
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ExcelPreviewItem(
    val courseCode: String,
    val courseName: String,
    val lecturer: String
)

data class ClassroomPreviewItem(
    val roomCode: String,
    val capacity: Int,
    val department: String
)

@Singleton
class ExcelManager @Inject constructor(
    private val teacherRepository: TeacherRepository
) {
    private val TAG = "ExcelManager"
    private val secureRandom = SecureRandom()
    private val passwordChars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"

    private fun normalizeForKey(value: String): String {
        return value.lowercase(Locale("tr"))
            .replace('\u0131', 'i')
            .replace('\u015f', 's')
            .replace('\u011f', 'g')
            .replace('\u00fc', 'u')
            .replace('\u00f6', 'o')
            .replace('\u00e7', 'c')
            .replace(Regex("[^a-z0-9]+"), "")
            .trim()
    }

    private fun buildUsername(name: String, surname: String): String {
        val normalizedName = normalizeForKey(name)
        val normalizedSurname = normalizeForKey(surname)
        return listOf(normalizedName, normalizedSurname)
            .filter { it.isNotBlank() }
            .joinToString("_")
            .ifBlank { "hoca" }
    }

    private fun generateInitialPassword(): String {
        return buildString {
            repeat(6) {
                append(passwordChars[secureRandom.nextInt(passwordChars.length)])
            }
        }
    }

    suspend fun getPreview(context: Context, uri: Uri): Result<List<ExcelPreviewItem>> = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)

            // Format validation: check column 2 has lecturer-like content
            val hasValidLecturerColumn = (1..minOf(3, sheet.lastRowNum)).any { i ->
                val row = sheet.getRow(i) ?: return@any false
                val col2 = row.getCell(2)?.toString()?.trim() ?: ""
                col2.isNotEmpty() && (col2.contains(".") || col2.contains("Prof", ignoreCase = true) || col2.contains("Dr", ignoreCase = true) || col2.split(" ").size >= 2)
            }
            if (!hasValidLecturerColumn) {
                workbook.close()
                return@withContext Result.failure(
                    IllegalArgumentException("Bu dosya ders listesi formatında değil.\nBeklenen format: Course Code | Course Name | Lecturer\n\nSınıf verileri için Sınıf Yönetimi ekranındaki Excel butonunu kullanın.")
                )
            }

            val previewList = mutableListOf<ExcelPreviewItem>()

            val rowLimit = if (sheet.lastRowNum > 5) 5 else sheet.lastRowNum
            for (i in 1..rowLimit) {
                val row = sheet.getRow(i) ?: continue
                val code = row.getCell(0)?.toString()?.trim() ?: ""
                val name = row.getCell(1)?.toString()?.trim() ?: ""
                val lecturer = row.getCell(2)?.toString()?.trim() ?: ""
                
                if (code.isNotEmpty() || name.isNotEmpty() || lecturer.isNotEmpty()) {
                    previewList.add(ExcelPreviewItem(code, name, lecturer))
                }
            }
            workbook.close()
            Result.success(previewList)
        } catch (e: Throwable) {
            Log.e(TAG, "Önizleme hatası", e)
            Result.failure(e)
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }

    suspend fun importExcel(context: Context, uri: Uri): Result<ExcelImportReport> = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        try {
            val existingTeachers = teacherRepository.getAllTeachers().first().toMutableList()
            
            inputStream = context.contentResolver.openInputStream(uri)
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)

            // Format validation: check that at least one row has a valid lecturer (contains title-like words)
            val hasValidLecturerColumn = (1..minOf(3, sheet.lastRowNum)).any { i ->
                val row = sheet.getRow(i) ?: return@any false
                val col2 = row.getCell(2)?.toString()?.trim() ?: ""
                col2.isNotEmpty() && (col2.contains(".") || col2.contains("Prof", ignoreCase = true) || col2.contains("Dr", ignoreCase = true) || col2.split(" ").size >= 2)
            }
            if (!hasValidLecturerColumn) {
                workbook.close()
                return@withContext Result.failure(
                    IllegalArgumentException("Bu dosya ders listesi formatında değil.\nBeklenen format: Course Code | Course Name | Lecturer\n\nSınıf verileri için Sınıf Yönetimi ekranındaki Excel butonunu kullanın.")
                )
            }

            val departmentName = "Bilgisayar Muhendisligi"
            val departmentId = teacherRepository.ensureDepartment(departmentName)
            val coursesToInsert = mutableListOf<Course>()
            val generatedCredentials = mutableListOf<GeneratedCredential>()
            var count = 0

            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                
                val courseCode = row.getCell(0)?.toString()?.trim() ?: ""
                val courseName = row.getCell(1)?.toString()?.trim() ?: ""
                val lecturerFull = row.getCell(2)?.toString()?.trim() ?: ""

                if (courseCode.isNotEmpty() && lecturerFull.isNotEmpty()) {
                    val parts = lecturerFull.split(" ").filter { it.isNotBlank() }
                    val titleParts = parts.filter { it.contains(".") || it.equals("Dr", ignoreCase = true) }
                    val title = titleParts.joinToString(" ")
                    val nameParts = parts.filter { !titleParts.contains(it) }
                    
                    val name = if (nameParts.isNotEmpty()) nameParts.first().trim() else ""
                    val surname = if (nameParts.size > 1) nameParts.drop(1).joinToString(" ").trim() else ""

                    if (name.isEmpty()) continue

                    // Dedup: check local mutable list first (prevents stale-list duplicates)
                    val normalizedName = name.lowercase().replace('ı','i').replace('ş','s').replace('ğ','g').replace('ü','u').replace('ö','o').replace('ç','c').trim()
                    val normalizedSurname = surname.lowercase().replace('ı','i').replace('ş','s').replace('ğ','g').replace('ü','u').replace('ö','o').replace('ç','c').trim()

                    val localExisting = existingTeachers.find {
                        val n = it.name.lowercase().replace('ı','i').replace('ş','s').replace('ğ','g').replace('ü','u').replace('ö','o').replace('ç','c').trim()
                        val s = it.surname.lowercase().replace('ı','i').replace('ş','s').replace('ğ','g').replace('ü','u').replace('ö','o').replace('ç','c').trim()
                        n == normalizedName && s == normalizedSurname
                    }

                    val teacherId: Int
                    val teacherForAccount: Teacher
                    if (localExisting != null) {
                        teacherId = localExisting.id
                        teacherForAccount = localExisting
                    } else {
                        teacherId = teacherRepository.getOrInsertTeacherOptimized(
                            Teacher(
                                name = name,
                                surname = surname,
                                title = title,
                                department = departmentName,
                                departmentId = departmentId
                            ),
                            existingTeachers
                        ).toInt()
                        // Add to local list to prevent duplicates for subsequent rows
                        teacherForAccount = Teacher(
                            id = teacherId,
                            name = name,
                            surname = surname,
                            title = title,
                            department = departmentName,
                            departmentId = departmentId
                        )
                        existingTeachers.add(teacherForAccount)
                    }

                    teacherRepository.createInitialTeacherAccountIfMissing(
                        teacher = teacherForAccount,
                        baseUsername = buildUsername(name, surname),
                        initialPassword = generateInitialPassword()
                    )?.let { generatedCredentials.add(it) }

                    val courseId = "${courseCode}_${teacherId}"
                    coursesToInsert.add(
                        Course(
                            id = courseId,
                            code = courseCode,
                            name = courseName,
                            departmentId = departmentId,
                            teacherId = teacherId
                        )
                    )
                    count++
                }
            }
            
            // Batch insert: single Firebase updateChildren() call
            if (coursesToInsert.isNotEmpty()) {
                teacherRepository.insertCoursesBatch(coursesToInsert)
            }

            workbook.close()
            Result.success(ExcelImportReport(count, generatedCredentials))
        } catch (e: Throwable) {
            Log.e(TAG, "Aktarım hatası", e)
            Result.failure(e)
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }

    // ========== Classroom Excel Import ==========

    suspend fun getClassroomPreview(context: Context, uri: Uri): Result<List<ClassroomPreviewItem>> = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)

            // Format validation: column 1 must be numeric (capacity)
            val hasNumericCapacity = (1..minOf(3, sheet.lastRowNum)).any { i ->
                val row = sheet.getRow(i) ?: return@any false
                val col1 = row.getCell(1)?.toString()?.trim() ?: ""
                col1.toDoubleOrNull() != null
            }
            if (!hasNumericCapacity) {
                workbook.close()
                return@withContext Result.failure(
                    IllegalArgumentException("Bu dosya sınıf listesi formatında değil.\nBeklenen format: Room Code | Capacity | Department")
                )
            }

            val previewList = mutableListOf<ClassroomPreviewItem>()

            val rowLimit = if (sheet.lastRowNum > 5) 5 else sheet.lastRowNum
            for (i in 1..rowLimit) {
                val row = sheet.getRow(i) ?: continue
                val roomCode = row.getCell(0)?.toString()?.trim() ?: ""
                val capacityRaw = row.getCell(1)?.toString()?.trim() ?: "0"
                val department = row.getCell(2)?.toString()?.trim() ?: ""

                val capacity = capacityRaw.toDoubleOrNull()?.toInt() ?: 0

                if (roomCode.isNotEmpty()) {
                    previewList.add(ClassroomPreviewItem(roomCode, capacity, department))
                }
            }
            workbook.close()
            Result.success(previewList)
        } catch (e: Throwable) {
            Log.e(TAG, "Sınıf önizleme hatası", e)
            Result.failure(e)
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }

    suspend fun importClassrooms(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)

            // Format validation: column 1 must be numeric (capacity)
            val hasNumericCapacity = (1..minOf(3, sheet.lastRowNum)).any { i ->
                val row = sheet.getRow(i) ?: return@any false
                val col1 = row.getCell(1)?.toString()?.trim() ?: ""
                col1.toDoubleOrNull() != null
            }
            if (!hasNumericCapacity) {
                workbook.close()
                return@withContext Result.failure(
                    IllegalArgumentException("Bu dosya sınıf listesi formatında değil.\nBeklenen format: Room Code | Capacity | Department")
                )
            }

            val classroomsToInsert = mutableListOf<Classroom>()

            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val roomCode = row.getCell(0)?.toString()?.trim() ?: ""
                val capacityRaw = row.getCell(1)?.toString()?.trim() ?: "0"
                val department = row.getCell(2)?.toString()?.trim() ?: ""

                val capacity = capacityRaw.toDoubleOrNull()?.toInt() ?: 0

                if (roomCode.isNotEmpty()) {
                    classroomsToInsert.add(
                        Classroom(roomCode = roomCode, capacity = capacity, department = department)
                    )
                }
            }

            // Batch insert: single Firebase updateChildren() call
            if (classroomsToInsert.isNotEmpty()) {
                teacherRepository.insertClassroomsBatch(classroomsToInsert)
            }

            workbook.close()
            Result.success(classroomsToInsert.size)
        } catch (e: Throwable) {
            Log.e(TAG, "Sınıf aktarım hatası", e)
            Result.failure(e)
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }
}

