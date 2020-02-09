/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.runners.executeState
import com.intellij.execution.ui.RunContentDescriptor
import org.jetbrains.concurrency.resolvedPromise

abstract class RsDefaultProgramRunnerBase : ProgramRunner<RunnerSettings> {

    @Throws(ExecutionException::class)
    final override fun execute(environment: ExecutionEnvironment) {
        val state = environment.state ?: return
        execute(environment, environment.callback, state)
    }

    protected open fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        return executeState(state, environment, this)
    }

    // BACKCOMPAT: 2019.3. merge it into [execute] method
    @Throws(ExecutionException::class)
    protected open fun execute(
        environment: ExecutionEnvironment,
        callback: ProgramRunner.Callback?,
        state: RunProfileState
    ) {
        @Suppress("UnstableApiUsage")
        ExecutionManager.getInstance(environment.project).startRunProfile(environment) {
            resolvedPromise(doExecute(state, environment))
        }
    }
}
