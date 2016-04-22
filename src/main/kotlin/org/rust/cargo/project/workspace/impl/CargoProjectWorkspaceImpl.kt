package org.rust.cargo.project.workspace.impl

import com.google.common.util.concurrent.SettableFuture
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.application.ApplicationManager
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
import org.rust.cargo.project.workspace.CargoProjectWorkspaceListener
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.cargoLibraryName
import org.rust.cargo.util.cargoProjectRoot
import org.rust.cargo.util.enqueue
import org.rust.cargo.util.updateLibrary
import org.rust.lang.utils.lock
import org.rust.lang.utils.release
import org.rust.lang.utils.usingWith
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock


class CargoProjectWorkspaceImpl(private val module: Module)
    : CargoProjectWorkspace
    , BulkFileListener {

    private val DELAY = 1000 /* milliseconds */

    private val LOG = Logger.getInstance(CargoProjectWorkspaceImpl::class.java)

    /**
     * Alarm used to coalesce consecutive update requests.
     * It uses pooled thread.
     */
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, null)

    /**
     * Lock to guard reads/updates to [cached]
     */
    private var lock: Lock = ReentrantLock()

    /**
     * Cached instance of the latest [CargoProjectDescription] instance synced with `Cargo.toml`
     */
    private var cached: CargoProjectDescription? = null

    init {
        usingWith (module.messageBus.connect()) {
            c -> c.subscribe(VirtualFileManager.VFS_CHANGES, this)
        }
    }

    /**
     * Works in two phases. First `cargo metadata` is executed on the background thread. Then,
     * the actual Library update happens on the event dispatch thread.
     */
    override fun requestUpdate(toolchain: RustToolchain, immediately: Boolean) {
        val contentRoot = module.cargoProjectRoot ?: return

        val delay = if (ApplicationManager.getApplication().isUnitTestMode) 0 else DELAY

        val task = UpdateTask(toolchain, contentRoot.path, showFeedback = false)

        alarm.cancelAllRequests()

        if (immediately) {
            task.enqueue()
        } else {
            alarm.addRequest({ task.enqueue() }, delay)
        }
    }

    override val projectDescription: CargoProjectDescription
        get() =
            lock (lock) {
                cached ?: release (lock) {
                    SettableFuture
                        .create<CargoProjectDescription>()
                        .let { f ->
                            usingWith (module.messageBus.connect()) {
                                c -> c.subscribe(
                                        CargoProjectWorkspace.UPDATES,
                                        object : CargoProjectWorkspaceListener {
                                            override fun onProjectUpdated(projectDescription: CargoProjectDescription) {
                                                f.set(projectDescription)
                                            }
                                        })
                            }

                            requestUpdate(module.toolchain!!)
                            f.get()
                        }
                }
            }

    override fun before(events: MutableList<out VFileEvent>) {}

    override fun after(events: MutableList<out VFileEvent>) {
        if (!module.project.rustSettings.autoUpdateEnabled) return
        val toolchain = module.toolchain ?: return

        val needsUpdate = events.any {
            val file = it.file ?: return@any false
            file.name == RustToolchain.CARGO_TOML && ModuleUtilCore.findModuleForFile(file, module.project) == module
        }

        if (needsUpdate) {
            requestUpdate(toolchain)
        }
    }

    private fun updateCargoProjectState(state: CargoProjectState) {
        val projectData = state.projectData
        if (projectData != null)
            lock (lock) {
                CargoProjectDescription.deserialize(projectData)?.let {
                    module.messageBus
                        .syncPublisher(CargoProjectWorkspace.UPDATES)
                        .onProjectUpdated(it)

                    cached = it
                }
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

                            updateCargoProjectState(CargoProjectState(result.cargoProject.serialize()))

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

    @TestOnly
    fun setState(cargoProject: CargoProjectDescriptionData) {
        updateCargoProjectState(CargoProjectState(cargoProject))
    }
}


data class CargoProjectState(
    var projectData: CargoProjectDescriptionData? = null
)

