package org.rust.bsp

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.rust.bsp.service.BspConnectionService

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

class BspStartupActivity : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        doRunActivity(project)
    }

    private fun doRunActivity(project: Project) {
        collectProject(project)
    }

    private fun prepareBackgroundTask(
        project: Project,
        name: String,
        cancelable: Boolean,
        beforeRun: () -> Unit = {},
        afterOnSuccess: () -> Unit = {}
    ) = object : Task.Backgroundable(project, name, cancelable) {

        override fun run(indicator: ProgressIndicator) {
            beforeRun()
        }

        override fun onSuccess() {
            afterOnSuccess()
        }

    }

    private fun collectProject(project: Project) {
        prepareBackgroundTask(
            project,
            "Connecting with BSP",
            true,
            beforeRun = { project.service<BspConnectionService>().connect() },
            afterOnSuccess = { println("Project collection finished") }
        ).queue()
    }
}
