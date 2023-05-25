package org.rust.bsp

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.rust.bsp.service.BspConnectionService

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

class BspStartupActivity : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        if (project.service<BspConnectionService>().hasBspServer()) {
            doRunActivity(project)
        }
    }

    private fun doRunActivity(project: Project) {
        collectProject(project)
    }

    private fun collectProject(project: Project) {
        runBackgroundableTask("Connecting with BSP", project, true) {
            project.service<BspConnectionService>().connect()
        }
    }
}
