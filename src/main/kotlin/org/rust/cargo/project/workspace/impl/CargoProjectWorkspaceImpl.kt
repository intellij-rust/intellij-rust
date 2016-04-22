package org.rust.cargo.project.workspace.impl

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.project.CargoProjectDescriptionData
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.*
import java.util.concurrent.Future
import kotlin.properties.Delegates


@State(
    name = "CargoMetadata",
    storages = arrayOf(Storage(file = StoragePathMacros.MODULE_FILE))
)
class CargoProjectWorkspaceImpl(private val module: Module) : CargoProjectWorkspace, PersistentStateComponent<CargoProjectState>, BulkFileListener {

    private val DELAY = 1000 /* milliseconds */

    private val LOG = Logger.getInstance(CargoProjectWorkspaceImpl::class.java)

    /**
     * Alarm used to coalesce consecutive update requests.
     * It uses pooled thread.
     */
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, null)

    private var cargoProjectState: CargoProjectState by Delegates.observable(CargoProjectState()) {
        prop, old, new ->
            projectDescription = new.projectData?.let { CargoProjectDescription.deserialize(it) }
    }

    override var projectDescription: CargoProjectDescription? = null

    init {
        module.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, this)
    }

    @TestOnly
    fun setState(cargoProject: CargoProjectDescriptionData) {
        cargoProjectState = CargoProjectState(cargoProject)
    }

    /**
     * Works in two phases. First `cargo metadata` is executed on the background thread. Then,
     * the actual Library update happens on the event dispatch thread.
     */
    override fun scheduleUpdate(toolchain: RustToolchain): Future<CargoProjectDescription> {
        val contentRoot = module.cargoProjectRoot ?: return Futures.immediateCancelledFuture<CargoProjectDescription>()

        val delay = if (ApplicationManager.getApplication().isUnitTestMode) 0 else DELAY

        val task = UpdateTask(toolchain, contentRoot.path, showFeedback = false)

        alarm.cancelAllRequests()

        val f = SettableFuture.create<CargoProjectDescription>()

        alarm.addRequest({ task.enqueue().flowIntoDiscardingResult(f, { projectDescription }) }, delay)

        return f
    }

    override fun updateNow(toolchain: RustToolchain): Future<CargoProjectDescription> {
        val contentRoot = module.cargoProjectRoot ?: return Futures.immediateCancelledFuture<CargoProjectDescription>()

        val task = UpdateTask(toolchain, contentRoot.path, showFeedback = true)

        alarm.cancelAllRequests()

        return task .enqueue()
                    .flowIntoDiscardingResult(SettableFuture.create<CargoProjectDescription>(), { projectDescription })
    }

    override fun loadState(state: CargoProjectState?) {
        cargoProjectState = state ?: CargoProjectState()
    }

    override fun getState(): CargoProjectState = cargoProjectState

    override fun before(events: MutableList<out VFileEvent>) {
    }

    override fun after(events: MutableList<out VFileEvent>) {
        if (!module.project.rustSettings.autoUpdateEnabled) return
        val toolchain = module.toolchain ?: return

        val needsUpdate = events.any {
            val file = it.file ?: return@any false
            file.name == RustToolchain.CARGO_TOML && ModuleUtilCore.findModuleForFile(file, module.project) == module
        }
        if (needsUpdate) {
            scheduleUpdate(toolchain)
        }
    }

    private inner class UpdateTask(
        private val toolchain: RustToolchain,
        private val projectDirectory: String,
        private val showFeedback: Boolean
    ) : Task.Backgroundable(module.project, "Updating cargo") {

        private var result: Result? = null

        override fun run(indicator: ProgressIndicator) {
            LOG.info("Cargo project update started")
            if (!toolchain.looksLikeValidToolchain()) {
                result = Result.Err(ExecutionException("Invalid toolchain $toolchain"))
                return
            }

            val cargo = toolchain.cargo(projectDirectory)
            result = try {
                val description = cargo.fullProjectDescription(object : ProcessAdapter() {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<Any>) {
                        val text = event.text.trim { it <= ' ' }
                        if (text.startsWith("Updating") || text.startsWith("Downloading")) {
                            indicator.text = text
                        }
                    }
                })

                Result.Ok(description)
            } catch (e: ExecutionException) {
                Result.Err(e)
            }
        }

        override fun onSuccess() {
            val result = requireNotNull(result)

            when (result) {
                is Result.Err -> {
                    showBalloon("Project '${module.project.name}' update failed: ${result.error.message}", MessageType.ERROR)
                    LOG.info("Project '${module.project.name}' update failed", result.error)
                }

                is Result.Ok  ->
                    ApplicationManager.getApplication().runWriteAction {
                        if (!module.isDisposed) {
                            val libraryRoots =
                                result.cargoProject.packages
                                    .filter { !it.isModule }
                                    .mapNotNull { it.virtualFile }

                            module.updateLibrary(module.cargoLibraryName, libraryRoots)
                            cargoProjectState = CargoProjectState(result.cargoProject.serialize())

                            showBalloon("Project '${module.project.name}' successfully updated!", MessageType.INFO)

                            LOG.info("Project '${module.project.name}' successfully updated")
                        }
                    }
            }
        }

        private fun showBalloon(message: String, type: MessageType) {
            if (showFeedback) {
                PopupUtil.showBalloonForActiveComponent(message, type)
            }
        }
    }

    private sealed class Result {
        class Ok(val cargoProject: CargoProjectDescription) : Result()
        class Err(val error: ExecutionException) : Result()
    }
}


data class CargoProjectState(
    var projectData: CargoProjectDescriptionData? = null
)

