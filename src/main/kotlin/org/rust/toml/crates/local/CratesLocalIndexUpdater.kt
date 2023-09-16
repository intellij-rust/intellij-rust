/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.Alarm
import com.intellij.util.io.write
import org.jetbrains.annotations.VisibleForTesting
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.tools.cargo
import org.rust.openapiext.RsPathManager
import org.rust.openapiext.RsProcessExecutionException
import org.rust.openapiext.checkIsDispatchThread
import org.rust.openapiext.execute
import org.rust.stdext.RsResult
import org.rust.toml.RsTomlBundle
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

@Service
class CratesLocalIndexUpdater : Disposable {

    private val alarm: Alarm = Alarm(this)

    // Should be changed only on EDT
    private var isUpdating: Boolean = false

    fun updateCratesIoGitIndex() {
        checkIsDispatchThread()
        if (isUpdating) return
        // If there isn't any open project with Rust, we don't need to update anything
        // because there isn't client for the corresponding data.
        // When next project with Rust is opened,
        // its project model update will trigger `org.rust.toml.crates.local.CratesLocalIndexWaker`
        // which invokes `updateCratesIoGitIndex` again
        if (!hasOpenRustProject) return

        val lastUpdate = PropertiesComponent.getInstance().getLong(CRATES_IO_INDEX_LAST_UPDATE, 0)
        val sinceLastUpdate = System.currentTimeMillis() - lastUpdate

        if (sinceLastUpdate < updateIntervalMillis) {
            if (alarm.isEmpty) {
                scheduleUpdate(updateIntervalMillis - sinceLastUpdate.toInt())
            }
            return
        }
        isUpdating = true
        alarm.cancelAllRequests()

        object : Task.Backgroundable(null, RsTomlBundle.message("rust.update.crates.index.progress.title")) {
            override fun run(indicator: ProgressIndicator) {
                LOG.info("crates.io index update started")

                val isSuccessful = updateCratesIoGitIndex(this@CratesLocalIndexUpdater)
                if (isSuccessful) {
                    PropertiesComponent.getInstance().setValue(CRATES_IO_INDEX_LAST_UPDATE, System.currentTimeMillis().toString())
                }
            }

            override fun onSuccess() {
                val index = CratesLocalIndexService.getInstance() as? CratesLocalIndexServiceImpl ?: return
                index.recoverIfNeeded()
            }

            override fun onFinished() {
                onUpdateFinished()
            }
        }.queue()
    }

    private fun onUpdateFinished() {
        LOG.info("crates.io index update finished")
        isUpdating = false
        scheduleUpdate(updateIntervalMillis)
    }

    private fun scheduleUpdate(delay: Int) {
        if (alarm.isEmpty) {
            alarm.addRequest(::updateCratesIoGitIndex, delay, true)
            LOG.info("crates.io index update is scheduled in $delay ms")
        }
    }

    override fun dispose() {}

    companion object {
        private val LOG = logger<CratesLocalIndexUpdater>()

        private const val CRATES_IO_INDEX_LAST_UPDATE = "CRATES_IO_INDEX_LAST_UPDATE"

        private const val DEFAULT_UPDATE_INTERVAL_MIN = 60
        private const val MIN_UPDATE_INTERVAL_MIN = 1

        private const val UPDATE_PROJECT_DIR_NAME = "crate_index_update_project"

        private val UPDATE_PROJECT_FILES = listOf(
            // Cargo.toml should contain external dependency to force cargo to load crates index
            // language=TOML
            FileTemplate(CargoConstants.MANIFEST_FILE, """
                [package]
                name = "crate_index_update_project"
                version = "0.1.0"
                edition = "2021"

                # See more keys and their definitions at https://doc.rust-lang.org/cargo/reference/manifest.html

                [dependencies]
                serde = "1.0"
            """.trimIndent()),
            FileTemplate("src/lib.rs", "")
        )

        private val hasOpenRustProject: Boolean
            get() {
                return ProjectManager.getInstance().openProjects.any {
                    !it.getServiceIfCreated(CargoProjectsService::class.java)?.allProjects.isNullOrEmpty()
                }
            }

        private val updateIntervalMillis: Int get() {
            val intervalMin = maxOf(
                MIN_UPDATE_INTERVAL_MIN,
                Registry.intValue("org.rust.crates.local.index.update.interval", DEFAULT_UPDATE_INTERVAL_MIN)
            )
            val intervalMillis = TimeUnit.MINUTES.toMillis(intervalMin.toLong())
            return if (intervalMillis > Int.MAX_VALUE) Int.MAX_VALUE else intervalMillis.toInt()
        }

        fun getInstance(): CratesLocalIndexUpdater = service()

        @VisibleForTesting
        fun updateCratesIoGitIndex(disposable: Disposable): Boolean {
            val projectPath = RsPathManager.pluginDirInSystem().resolve(UPDATE_PROJECT_DIR_NAME)
            val projectCreated = createUpdateProjectIfNeeded(projectPath)
            if (!projectCreated) return false

            val toolchain = RsToolchainBase.suggest(projectPath) ?: return false
            return toolchain.triggerCratesIoGitIndexUpdate(disposable, projectPath)
        }

        private fun createUpdateProjectIfNeeded(projectPath: Path): Boolean {
            return try {
                for ((path, text) in UPDATE_PROJECT_FILES) {
                    val projectFilePath = projectPath.resolve(path)
                    if (!projectFilePath.exists()) {
                        projectFilePath.write(text)
                    }
                }
                true
            } catch (e: IOException) {
                LOG.error("Failed to create update project at $projectPath", e)
                false
            }
        }

        private fun RsToolchainBase.triggerCratesIoGitIndexUpdate(disposable: Disposable, projectPath: Path): Boolean {
            val cargo = cargo()

            // Force cargo to use git protocol to load the whole crates.io index
            val envs = EnvironmentVariablesData.create(mapOf("CARGO_REGISTRIES_CRATES_IO_PROTOCOL" to "git"), true)
            // There is no direct cargo command to update crates.io registry index.
            // So let's call some cheap command which resolves dependencies,
            // for example, `cargo metadata`

            val result = createGeneralCommandLine(
                executable = cargo.executable,
                workingDirectory = projectPath,
                redirectInputFrom = null,
                backtraceMode = BacktraceMode.FULL,
                environmentVariables = envs,
                parameters = listOf("metadata", "--format-version", "1"),
                emulateTerminal = false,
                withSudo = false
            ).execute(disposable)

            if (result is RsResult.Err) {
                if (result.err is RsProcessExecutionException.Canceled) {
                    LOG.info("Updating crates.io index was cancelled", result.err)
                } else {
                    LOG.error("Failed to update crates.io index", result.err)
                }
            }
            return result.isOk
        }
    }

    private data class FileTemplate(val path: String, val text: String)
}
