package com.example.edusync.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Login : Screen("login", "Giriş", Icons.Default.Login)
    object ActivateAccount : Screen("activate_account", "Hesap Aktive Et", Icons.Default.HowToReg)
    
    // Admin Screens
    object AdminDashboard : Screen("admin_dashboard", "Panel", Icons.Default.Dashboard)
    object TeacherManagement : Screen("teacher_management", "Hocalar", Icons.Default.People)
    object VerificationCodes : Screen("verification_codes", "Kayıt Kodları", Icons.Default.Key)
    object ExcelImport : Screen("excel_import", "Excel İşlemleri", Icons.Default.FileUpload)
    object AdminMessages : Screen("admin_messages", "Mesajlar", Icons.Default.Chat)
    object Classrooms : Screen("classrooms", "Sınıflar", Icons.Default.MeetingRoom)
    object Assignments : Screen("assignments", "Atamalar", Icons.Default.Assignment)
    object GlobalSchedule : Screen("global_schedule", "Genel Program", Icons.Default.DateRange)
    
    // Teacher Screens
    object TeacherHome : Screen("teacher_home/{teacherId}", "Ana Sayfa", Icons.Default.Home) {
        fun createRoute(teacherId: Int) = "teacher_home/$teacherId"
    }
    object TeacherSchedule : Screen("teacher_schedule/{teacherId}", "Program", Icons.Default.CalendarMonth) {
        fun createRoute(teacherId: Int) = "teacher_schedule/$teacherId"
    }
    object TeacherMessages : Screen("teacher_messages", "Mesajlar", Icons.Default.Chat)
    object TeacherSettings : Screen("teacher_settings", "Ayarlar", Icons.Default.Settings)
    object PasswordChange : Screen("password_change/{username}", "Sifre Degistir", Icons.Default.Password) {
        fun createRoute(username: String) = "password_change/$username"
    }

    // Common
    object ChatDetail : Screen("chat_detail/{targetUserId}", "Sohbet", Icons.Default.Chat) {
        fun createRoute(targetUserId: String) = "chat_detail/$targetUserId"
    }
}

val adminBottomNavItems = listOf(
    Screen.AdminDashboard,
    Screen.TeacherManagement,
    Screen.GlobalSchedule,
    Screen.AdminMessages
)

val teacherBottomNavItems = listOf(
    Screen.TeacherHome,
    Screen.TeacherSchedule,
    Screen.TeacherMessages
)
