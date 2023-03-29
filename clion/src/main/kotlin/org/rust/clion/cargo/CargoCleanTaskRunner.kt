/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.cargo

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTask
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskRunner
import com.jetbrains.cidr.execution.build.tasks.CidrCleanTask
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.rejectedPromise
import org.rust.cargo.runconfig.cleanProject

class CargoCleanTaskRunner : ProjectTaskRunner() {

    override fun run(project: Project, context: ProjectTaskContext, vararg tasks: ProjectTask): Promise<Result> {
        if (project.isDisposed) return rejectedPromise("Project is already disposed")
        invokeLater { project.cleanProject() }
        return rejectedPromise()
    }

    override fun canRun(projectTask: ProjectTask): Boolean = projectTask is CidrCleanTask
}
