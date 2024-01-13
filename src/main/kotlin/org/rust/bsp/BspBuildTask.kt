@file:Suppress("UnstableApiUsage")

package org.rust.bsp

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.bsp4j.CompileParams
import ch.epfl.scala.bsp4j.StatusCode
import com.intellij.build.BuildContentDescriptor
import com.intellij.build.BuildDescriptor
import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.rust.RsTask
import org.rust.bsp.service.BspConnectionService
import org.rust.cargo.project.settings.rustSettings
import javax.swing.JComponent

class BspBuildTask(
    project: Project,
    private val bspTargets: List<BuildTargetIdentifier>
) : Task.Backgroundable(project, "Building Bsp targets", true), RsTask {
    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true

        val buildProgress = BuildViewManager.createBuildProgress(project)

        buildProgress.start(createBuildProgressDescriptor(indicator))
        buildProgress.output("Started building targets :\n", true)
        for (target in bspTargets) {
            buildProgress.output("  ${target.uri}\n", true)
        }

        val connection = project.service<BspConnectionService>()
        val result = connection.compileSolution(CompileParams(bspTargets)).get()
        buildProgress.output("Finished building targets\n", true)

        if (result.statusCode == StatusCode.OK) {
            buildProgress.output("Build succeeded\n", true)
            buildProgress.finish()
        } else if (result.statusCode == StatusCode.CANCELLED) {
            buildProgress.output("Build canceled\n", true)
            buildProgress.cancel()
        } else if (result.statusCode == StatusCode.ERROR) {
            buildProgress.output("Build failed\n", true)
            buildProgress.fail()
        } else {
            buildProgress.output("Unknown status code\n", true)
            buildProgress.fail()
        }

    }

    private fun createBuildProgressDescriptor(progress: ProgressIndicator): BuildProgressDescriptor {
        val buildContentDescriptor = BuildContentDescriptor(null, null, object : JComponent() {}, "Bsp")
        buildContentDescriptor.isActivateToolWindowWhenFailed = true
        buildContentDescriptor.isActivateToolWindowWhenAdded = true
        buildContentDescriptor.isNavigateToError = project.rustSettings.autoShowErrorsInEditor
        val descriptor = DefaultBuildDescriptor(Any(), "Build", project.basePath!!, System.currentTimeMillis())
            .withContentDescriptor { buildContentDescriptor }
        return object : BuildProgressDescriptor {
            override fun getTitle(): String = descriptor.title
            override fun getBuildDescriptor(): BuildDescriptor = descriptor
        }
    }
}
