package com.goonanimation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.goonanimation.ui.screen.editor.EditorScreen
import com.goonanimation.ui.screen.home.HomeScreen
import com.goonanimation.ui.theme.GoonAnimationTheme

/**
 * Single-activity architecture entry point.
 * Navigation is handled by Compose Navigation with two destinations:
 * - "home" → Project library
 * - "editor?projectId={id}&projectName={name}" → Animation editor
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GoonAnimationTheme {
                GoonAnimationNavGraph()
            }
        }
    }
}

@Composable
private fun GoonAnimationNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {

        composable("home") {
            HomeScreen(
                onOpenProject = { projectId ->
                    navController.navigate("editor?projectId=$projectId")
                },
                onNewProject = { name ->
                    navController.navigate("editor?projectName=${encodeUriComponent(name)}")
                }
            )
        }

        composable(
            route = "editor?projectId={projectId}&projectName={projectName}",
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("projectName") {
                    type = NavType.StringType
                    defaultValue = "New Animation"
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId")
            val projectName = backStackEntry.arguments?.getString("projectName") ?: "New Animation"
            EditorScreen(
                projectId = projectId,
                projectName = projectName,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

/** Simple percent-encoding for navigation arguments */
private fun encodeUriComponent(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8")
