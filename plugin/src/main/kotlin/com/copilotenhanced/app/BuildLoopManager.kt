package com.copilotenhanced.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

object BuildLoopManager {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    var currentJob: Job? = null

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
    }
}
