package org.rust.cargo.runconfig

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.DefaultProgramRunner

class RsRunner : DefaultProgramRunner() {
    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultRunExecutor.EXECUTOR_ID && profile is CargoCommandConfiguration

    override fun getRunnerId(): String = "RustRunner"
}
