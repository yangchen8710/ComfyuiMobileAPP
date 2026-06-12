package com.comfyui.client.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.comfyui.client.data.repository.WorkflowRepository
import com.comfyui.client.ui.screen.*
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val WORKFLOW_LIST = "workflow_list"
    const val NODE_CONFIG = "node_config/{workflowId}?historyId={historyId}"
    const val IMPORT = "import"
    const val SERVER_BROWSE = "server_browse"
    const val HISTORY_LIST = "history_list/{workflowId}"
    const val FULLSCREEN_PREVIEW = "fullscreen_preview/{imageUrl}"
    const val SETTINGS = "settings"

    fun nodeConfig(workflowId: String) = "node_config/$workflowId?historyId="
    fun nodeConfigWithHistory(workflowId: String, historyId: String) = "node_config/$workflowId?historyId=$historyId"
    fun historyList(workflowId: String) = "history_list/$workflowId"
    fun fullscreenPreview(imageUrl: String) = "fullscreen_preview/${URLEncoder.encode(imageUrl, "UTF-8")}"
    fun decodeUrl(encoded: String) = URLDecoder.decode(encoded, "UTF-8")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    repository: WorkflowRepository
) {
    NavHost(
        navController = navController,
        startDestination = Routes.WORKFLOW_LIST
    ) {
        composable(Routes.WORKFLOW_LIST) {
            WorkflowListScreen(
                repository = repository,
                onWorkflowClick = { workflowId ->
                    if (repository.hasHistory(workflowId)) {
                        navController.navigate(Routes.historyList(workflowId))
                    } else {
                        navController.navigate(Routes.nodeConfig(workflowId))
                    }
                },
                onImportClick = {
                    navController.navigate(Routes.IMPORT)
                },
                onServerBrowseClick = {
                    navController.navigate(Routes.SERVER_BROWSE)
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(
            route = Routes.NODE_CONFIG,
            arguments = listOf(
                navArgument("workflowId") { type = NavType.StringType },
                navArgument("historyId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val workflowId = backStackEntry.arguments?.getString("workflowId") ?: return@composable
            val historyId = backStackEntry.arguments?.getString("historyId")
            NodeConfigScreen(
                workflowId = workflowId,
                historyId = historyId,
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.IMPORT) {
            ImportWorkflowScreen(
                repository = repository,
                onImported = { workflowId ->
                    navController.navigate(Routes.nodeConfig(workflowId)) {
                        popUpTo(Routes.IMPORT) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SERVER_BROWSE) {
            ServerWorkflowsScreen(
                repository = repository,
                onImported = { workflowId ->
                    navController.navigate(Routes.nodeConfig(workflowId)) {
                        popUpTo(Routes.SERVER_BROWSE) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.HISTORY_LIST,
            arguments = listOf(navArgument("workflowId") { type = NavType.StringType })
        ) { backStackEntry ->
            val wfId = backStackEntry.arguments?.getString("workflowId") ?: return@composable
            val workflow = repository.getWorkflow(wfId)
            HistoryListScreen(
                workflowId = wfId,
                workflowName = workflow?.name ?: "Unknown",
                repository = repository,
                onHistoryClick = { historyId ->
                    navController.navigate(Routes.nodeConfigWithHistory(wfId, historyId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.FULLSCREEN_PREVIEW,
            arguments = listOf(navArgument("imageUrl") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("imageUrl") ?: return@composable
            val imageUrl = Routes.decodeUrl(encodedUrl)
            FullscreenPreviewScreen(
                imageUrl = imageUrl,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
