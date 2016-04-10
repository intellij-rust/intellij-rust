package org.rust.cargo.toolchain.impl

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
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
import org.rust.cargo.CargoProjectDescription
import org.rust.cargo.toolchain.*

private val LOG = Logger.getInstance(CargoMetadataServiceImpl::class.java);

@State(
    name = "CargoMetadata",
    storages = arrayOf(Storage(file = StoragePathMacros.MODULE_FILE))
)
class CargoMetadataServiceImpl(private val module: Module) : CargoMetadataService, PersistentStateComponent<CargoProjectState>, BulkFileListener {
    private var cargoProjectState: CargoProjectState = CargoProjectState()

    // Alarm used to coalesce consecutive update requests.
    // It uses EDT thread, but the tasks are really tiny and
    // only spawn background update.
    private val alarm = Alarm()
    private val DELAY_MILLIS = 1000

    init {
        module.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, this)
    }

    /*
     * Works in two phases. First `cargo metadata` is executed on the background thread. Then,
     * the actual Library update happens on the event dispatch thread.
     */
    override fun scheduleUpdate(toolchain: RustToolchain) {
        val contentRoot = module.cargoProjectRoot ?: return

        val task = UpdateTask(toolchain, contentRoot.path, showFeedback = false)
        alarm.cancelAllRequests()
        val delay = if (ApplicationManager.getApplication().isUnitTestMode) 0 else DELAY_MILLIS
        alarm.addRequest({ task.queue() }, delay)
    }

    override fun updateNow(toolchain: RustToolchain) {
        val contentRoot = module.cargoProjectRoot ?: return

        val task = UpdateTask(toolchain, contentRoot.path, showFeedback = true)
        alarm.cancelAllRequests()
        task.queue()
    }

    override val cargoProject: CargoProjectDescription?
        get() = state.cargoProjectDescription

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
                    showBalloon("Cargo project update failed: ${result.error.message}", MessageType.ERROR)
                    LOG.info("Cargo project update failed", result.error)
                }
                is Result.Ok -> ApplicationManager.getApplication().runWriteAction {
                    if (!module.isDisposed) {
                        val libraryRoots = result.cargoProject.packages
                            .filter { !it.isModule }
                            .mapNotNull { it.virtualFile }

                        updateLibrary(module, module.cargoLibraryName, libraryRoots)
                        cargoProjectState.cargoProjectDescription = result.cargoProject
                        showBalloon("Cargo project successfully updated", MessageType.INFO)
                        LOG.info("Cargo project successfully updated")
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

