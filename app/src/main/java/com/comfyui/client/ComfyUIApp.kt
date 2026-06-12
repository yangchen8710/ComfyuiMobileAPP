package com.comfyui.client

import android.app.Application
import com.comfyui.client.data.repository.DanbooruRepository
import com.comfyui.client.data.repository.WorkflowRepository

class ComfyUIApp : Application() {

    lateinit var workflowRepository: WorkflowRepository
        private set

    lateinit var danbooruRepository: DanbooruRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        workflowRepository = WorkflowRepository()
        danbooruRepository = DanbooruRepository()

        // Load persisted workflows
        workflowRepository.initPrefs(this)

        // Auto-initialize with saved server URL
        val prefs = getSharedPreferences("comfyui_prefs", MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", "http://your-comfyui-server:8188") ?: ""
        if (savedUrl.isNotBlank()) {
            workflowRepository.setServerUrl(savedUrl)
        }
    }

    companion object {
        lateinit var instance: ComfyUIApp
            private set
    }
}
