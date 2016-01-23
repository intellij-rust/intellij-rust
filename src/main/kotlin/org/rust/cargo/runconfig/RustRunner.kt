package org.rust.cargo.runconfig

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.DefaultProgramRunner

class RustRunner : DefaultProgramRunner() {
    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        DefaultRunExecutor.EXECUTOR_ID == executorId && profile is RustApplicationConfiguration

    override fun getRunnerId(): String = "RustRunner"

}
