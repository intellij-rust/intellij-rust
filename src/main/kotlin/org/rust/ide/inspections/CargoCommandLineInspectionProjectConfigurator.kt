/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.ide.CommandLineInspectionProgressReporter
import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.ide.CommandLineInspectionProjectConfigurator.ConfiguratorContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.rust.RsBundle
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.guessAndSetupRustProject
import org.rust.cargo.project.model.impl.CargoProjectsServiceImpl
import org.rust.cargo.project.settings.rustSettings
import java.util.concurrent.CountDownLatch
import kotlin.io.path.exists

class CargoCommandLineInspectionProjectConfigurator : CommandLineInspectionProjectConfigurator {
    override fun getName(): String = "cargo"
    override fun getDescription(): String = RsBundle.message("cargo.commandline.description")

    override fun isApplicable(context: ConfiguratorContext): Boolean {
        // TODO: find all Cargo.toml in the project
        return context.projectPath.resolve(CargoConstants.MANIFEST_FILE).exists()
    }

    override fun configureEnvironment(context: ConfiguratorContext) {
        System.setProperty(CargoProjectsServiceImpl.CARGO_DISABLE_PROJECT_REFRESH_ON_CREATION, "true")
        // See `com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker.isDisabledAutoReload`
        Registry.get("external.system.auto.import.disabled").setValue(true)
    }

    override fun preConfigureProject(project: Project, context: ConfiguratorContext) {
        project.rustSettings.modify {
            it.autoUpdateEnabled = false
        }
    }

    override fun configureProject(project: Project, context: ConfiguratorContext) {
        val logger = LoggerWrapper(context.logger, logger<CargoCommandLineInspectionProjectConfigurator>())

        val refreshStarted = CountDownLatch(1)
        val refreshFinished = CountDownLatch(1)
        project.messageBus.connect().subscribe(
            CargoProjectsService.CARGO_PROJECTS_REFRESH_TOPIC,
            object : CargoProjectsService.CargoProjectsRefreshListener {
                override fun onRefreshStarted() {
                    logger.info("Cargo project model loading...")
                    refreshStarted.countDown()
                }

                override fun onRefreshFinished(status: CargoProjectsService.CargoRefreshStatus) {
                    logger.info("Cargo project model loading finished: $status")
                    refreshFinished.countDown()
                }
            }
        )

        val cargoProjectsService = project.cargoProjects

        val result = guessAndSetupRustProject(project, explicitRequest = true)
        if (!result) {
            if (cargoProjectsService.hasAtLeastOneValidProject) {
                cargoProjectsService.refreshAllProjects()
            } else {
                logger.error("Cargo project model loading failed to start")
                return
            }
        }

        ProgressIndicatorUtils.awaitWithCheckCanceled(refreshStarted)
        ProgressIndicatorUtils.awaitWithCheckCanceled(refreshFinished)

        for (cargoProject in cargoProjectsService.allProjects) {
            val status = cargoProject.mergedStatus
            if (status is CargoProject.UpdateStatus.UpdateFailed) {
                logger.error(status.reason)
            }
        }
    }

    private class LoggerWrapper(
        private val inspectionProgressReporter: CommandLineInspectionProgressReporter,
        private val logger: Logger
    ) {
        fun info(message: String) {
            logger.info(message)
            inspectionProgressReporter.reportMessage(1, message)
        }

        fun error(message: String) {
            logger.error(message)
            inspectionProgressReporter.reportError(message)
        }
    }
}
