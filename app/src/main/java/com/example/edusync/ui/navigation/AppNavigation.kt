package com.example.edusync.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.edusync.data.UserRole
import com.example.edusync.ui.*

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val sessionUser by sessionViewModel.sessionUser.collectAsStateWithLifecycle()

    var currentUserRole by remember { mutableStateOf<UserRole?>(null) }
    var currentUsername by remember { mutableStateOf<String?>(null) }
    var loggedInTeacherId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(sessionUser) {
        val user = sessionUser ?: return@LaunchedEffect
        if (currentUserRole != null) return@LaunchedEffect

        currentUserRole = user.role
        currentUsername = user.username
        loggedInTeacherId = user.teacherId

        when {
            user.role == UserRole.ADMIN -> {
                navController.navigate(Screen.AdminDashboard.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
            user.mustChangePassword -> {
                navController.navigate(Screen.PasswordChange.createRoute(user.username)) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
            user.teacherId != null -> {
                navController.navigate(Screen.TeacherHome.createRoute(user.teacherId!!)) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
        }
    }

    // PDF Optimization: Use derivedStateOf for layout decisions to minimize recomposition scopes.
    val showAdminBar by remember(currentUserRole, currentDestination) {
        derivedStateOf {
            currentUserRole == UserRole.ADMIN && 
            currentDestination?.route != Screen.Login.route && 
            currentDestination?.route != Screen.ActivateAccount.route &&
            currentDestination?.route != Screen.PasswordChange.route
        }
    }
    
    val showTeacherBar by remember(currentUserRole, currentDestination) {
        derivedStateOf {
            currentUserRole == UserRole.TEACHER && 
            currentDestination?.route != Screen.Login.route && 
            currentDestination?.route != Screen.ActivateAccount.route &&
            currentDestination?.route != Screen.PasswordChange.route
        }
    }

    Scaffold(
        bottomBar = {
            if (showAdminBar) {
                AdminBottomBar(navController, currentDestination)
            } else if (showTeacherBar) {
                TeacherBottomBar(navController, currentDestination, loggedInTeacherId)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController, 
            startDestination = Screen.Login.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = { user ->
                        currentUserRole = user.role
                        currentUsername = user.username
                        loggedInTeacherId = user.teacherId
                        if (user.role == UserRole.ADMIN) {
                            sessionViewModel.save(user)
                            navController.navigate(Screen.AdminDashboard.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        } else if (user.teacherId != null && user.mustChangePassword) {
                            navController.navigate(Screen.PasswordChange.createRoute(user.username)) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        } else if (user.teacherId != null) {
                            sessionViewModel.save(user)
                            navController.navigate(Screen.TeacherHome.createRoute(user.teacherId!!)) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(Screen.ActivateAccount.route) {
                ActivateAccountScreen(
                    onActivationSuccess = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.ActivateAccount.route) { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.PasswordChange.route,
                arguments = listOf(navArgument("username") { type = NavType.StringType })
            ) { backStackEntry ->
                val username = backStackEntry.arguments?.getString("username") ?: ""
                PasswordChangeScreen(
                    username = username,
                    onPasswordChanged = { user ->
                        currentUserRole = user.role
                        currentUsername = user.username
                        loggedInTeacherId = user.teacherId
                        sessionViewModel.save(user)
                        user.teacherId?.let { teacherId ->
                            navController.navigate(Screen.TeacherHome.createRoute(teacherId)) {
                                popUpTo(Screen.PasswordChange.route) { inclusive = true }
                            }
                        }
                    },
                    onLogout = {
                        currentUserRole = null
                        currentUsername = null
                        loggedInTeacherId = null
                        sessionViewModel.clear()
                        navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                    }
                )
            }

            composable(
                route = Screen.TeacherHome.route,
                arguments = listOf(navArgument("teacherId") { type = NavType.IntType })
            ) { backStackEntry ->
                val teacherId = backStackEntry.arguments?.getInt("teacherId") ?: 0
                TeacherHomeScreen(
                    teacherId = teacherId,
                    onOpenSchedule = { navController.navigate(Screen.TeacherSchedule.createRoute(teacherId)) },
                    onLogout = {
                        currentUserRole = null
                        currentUsername = null
                        loggedInTeacherId = null
                        sessionViewModel.clear()
                        navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                    }
                )
            }
            
            composable(
                route = Screen.TeacherSchedule.route,
                arguments = listOf(navArgument("teacherId") { type = NavType.IntType })
            ) { backStackEntry ->
                val teacherId = backStackEntry.arguments?.getInt("teacherId") ?: 0
                val showBackButton = currentUserRole == UserRole.ADMIN

                TeacherScheduleScreen(
                    teacherId = teacherId,
                    isReadOnly = currentUserRole == UserRole.ADMIN,
                    onNavigateBack = if (showBackButton) { { navController.popBackStack() } } else null,
                    onLogout = if (currentUserRole == UserRole.TEACHER) {
                        {
                            currentUserRole = null
                            currentUsername = null
                            loggedInTeacherId = null
                            sessionViewModel.clear()
                            navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                        }
                    } else null
                )
            }

            composable(Screen.TeacherMessages.route) {
                ChatDetailScreen(
                    currentUserId = currentUsername ?: "",
                    targetUserId = "admin",
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.TeacherSettings.route) {
                TeacherSettingsScreen(
                    onLogout = {
                        currentUserRole = null
                        currentUsername = null
                        loggedInTeacherId = null
                        sessionViewModel.clear()
                        navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                    }
                )
            }

            composable(Screen.AdminDashboard.route) {
                AdminDashboardScreen(
                    onLogout = {
                        currentUserRole = null
                        currentUsername = null
                        loggedInTeacherId = null
                        sessionViewModel.clear()
                        navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                    },
                    onNavigateToExcel = { navController.navigate(Screen.ExcelImport.route) },
                    onNavigateToClassrooms = { navController.navigate(Screen.Classrooms.route) },
                    onNavigateToAssignments = { navController.navigate(Screen.Assignments.route) }
                )
            }

            composable(Screen.TeacherManagement.route) {
                TeacherManagementScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onTeacherClick = { teacherId ->
                        navController.navigate(Screen.TeacherSchedule.createRoute(teacherId))
                    }
                )
            }

            composable(Screen.AdminMessages.route) {
                AdminMessagesScreen(
                    onChatClick = { targetUserId ->
                        navController.navigate(Screen.ChatDetail.createRoute(targetUserId))
                    }
                )
            }

            composable(
                route = Screen.ChatDetail.route,
                arguments = listOf(navArgument("targetUserId") { type = NavType.StringType })
            ) { backStackEntry ->
                val targetUserId = backStackEntry.arguments?.getString("targetUserId") ?: ""
                ChatDetailScreen(
                    currentUserId = if (currentUserRole == UserRole.ADMIN) "admin" else (currentUsername ?: ""),
                    targetUserId = targetUserId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.VerificationCodes.route) {
                AdminVerificationCodeScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ExcelImport.route) {
                AdminExcelImportScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.GlobalSchedule.route) {
                GlobalScheduleScreen()
            }

            composable(Screen.Classrooms.route) {
                ClassroomScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Assignments.route) {
                AssignmentScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun AdminBottomBar(
    navController: NavHostController, 
    currentDestination: NavDestination?,
    chatViewModel: ChatViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { chatViewModel.initUser("admin") }
    val totalUnreadCount by chatViewModel.totalUnreadCount.collectAsStateWithLifecycle()
    
    NavigationBar {
        adminBottomNavItems.forEach { screen ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            NavigationBarItem(
                icon = { 
                    BadgedBox(
                        badge = {
                            if (screen == Screen.AdminMessages && totalUnreadCount > 0) {
                                Badge { Text(totalUnreadCount.toString()) }
                            }
                        }
                    ) {
                        Icon(screen.icon, contentDescription = screen.title)
                    }
                },
                label = { Text(screen.title) },
                selected = isSelected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun TeacherBottomBar(
    navController: NavHostController, 
    currentDestination: NavDestination?,
    loggedInTeacherId: Int?,
    chatViewModel: ChatViewModel = hiltViewModel()
) {
    val totalUnreadCount by chatViewModel.totalUnreadCount.collectAsStateWithLifecycle()
    
    NavigationBar {
        teacherBottomNavItems.forEach { screen ->
            val route = when {
                screen is Screen.TeacherHome && loggedInTeacherId != null -> screen.createRoute(loggedInTeacherId)
                screen is Screen.TeacherSchedule && loggedInTeacherId != null -> screen.createRoute(loggedInTeacherId)
                else -> screen.route
            }
            
            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true ||
                    (screen is Screen.TeacherHome && currentDestination?.route?.startsWith("teacher_home") == true) ||
                    (screen is Screen.TeacherSchedule && currentDestination?.route?.startsWith("teacher_schedule") == true)

            NavigationBarItem(
                icon = { 
                    BadgedBox(
                        badge = {
                            if (screen == Screen.TeacherMessages && totalUnreadCount > 0) {
                                Badge { Text(totalUnreadCount.toString()) }
                            }
                        }
                    ) {
                        Icon(screen.icon, contentDescription = screen.title)
                    }
                },
                label = { Text(screen.title) },
                selected = isSelected,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
