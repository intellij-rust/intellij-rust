package org.rust.cargo.project.workspace.impl

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleComponent
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
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.project.workspace.CargoProjectWorkspaceListener
import org.rust.cargo.project.workspace.CargoProjectWorkspaceListener.UpdateResult
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.cargoLibraryName
import org.rust.cargo.util.cargoProjectRoot
import org.rust.cargo.util.enqueue
import org.rust.cargo.util.updateLibrary
import org.rust.lang.utils.lock
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock


/**
 * [CargoProjectWorkspace] component implementation
 */
class CargoProjectWorkspaceImpl(private val module: Module) : CargoProjectWorkspace, ModuleComponent {

    private val DELAY = 1000 /* milliseconds */

    private val LOG = Logger.getInstance(CargoProjectWorkspaceImpl::class.java)

    /**
     * Alarm used to coalesce consecutive update requests.
     * It uses pooled thread.
     */
    private val alarm = Alarm()

    /**
     * Lock to guard reads/updates to [cached]
     */
    private var lock: Lock = ReentrantLock()

    /**
     * Cached instance of the latest [CargoProjectDescription] instance synced with `Cargo.toml`
     */
    private var cached: CargoProjectDescription? = null

    /** Component hooks */

    override fun getComponentName(): String = "org.rust.cargo.CargoProjectWorkspace"

    override fun initComponent() {
        subscribeTo(VirtualFileManager.VFS_CHANGES, FileChangesWatcher())

        subscribeTo(CargoProjectWorkspaceListener.Topics.UPDATES, ProjectModelUpdater())
        //subscribeTo(CargoProjectWorkspaceListener.Topics.UPDATES, Notifier())
    }

    override fun disposeComponent() {
        alarm.dispose()
    }

    override fun projectClosed() { /* NOP */ }

    override fun projectOpened() { /* NOP */ }

    override fun moduleAdded() {
        module.toolchain?.let { requestUpdateUsing(it, immediately = true) }
    }

    /**
     * Requests to updates Rust libraries asynchronously. Consecutive requests are coalesced.
     *
     * Works in two phases. First `cargo metadata` is executed on the background thread. Then,
     * the actual Library update happens on the event dispatch thread.
     */
    override fun requestUpdateUsing(toolchain: RustToolchain, immediately: Boolean) {
        val contentRoot = module.cargoProjectRoot ?: return

        val delay = if (ApplicationManager.getApplication().isUnitTestMode) 0 else DELAY

        val task = UpdateTask(toolchain, contentRoot.path)

        alarm.cancelAllRequests()

        if (immediately)
            ApplicationManager.getApplication()
                .invokeLater({ task.enqueue() })
        else
            alarm.addRequest({ task.enqueue() }, delay, ModalityState.any())
    }

    /**
     * Delivers latest cached project-description instance
     *
     * NOTA BENE: [CargoProjectWorkspace] is rather low-level abstraction around, `Cargo.toml`-backed projects
     *            mapping underpinning state of the `Cargo.toml` workspace _transparently_, i.e. it doesn't provide
     *            any facade atop of the latter insulating itself from inconsistencies in the underlying layer. For
     *            example, [CargoProjectWorkspace] wouldn't be able to provide a valid [CargoProjectDescription] instance
     *            until the `Cargo.toml` becomes sound
     */
    override val projectDescription: CargoProjectDescription?
        get() = lock (lock) { cached }

    private fun notifyCargoProjectUpdate(r: UpdateResult) {
        ApplicationManager.getApplication().assertIsDispatchThread()

        if (!module.isDisposed)
            module.messageBus
                .syncPublisher(CargoProjectWorkspaceListener.Topics.UPDATES)
                .onWorkspaceUpdateCompleted(r)
    }

    private inner class UpdateTask(
        private val toolchain: RustToolchain,
        private val projectDirectory: String
    )
        : Task.Backgroundable(module.project, "Updating cargo") {

        private var result: UpdateResult? = null

        override fun run(indicator: ProgressIndicator) {
            LOG.info("Cargo project update started")

            if (!toolchain.looksLikeValidToolchain()) {
                result = UpdateResult.Err(ExecutionException("Invalid toolchain $toolchain"))
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

                UpdateResult.Ok(description)
            } catch (e: ExecutionException) {
                UpdateResult.Err(e)
            }
        }

        override fun onSuccess() {
            val r = requireNotNull(result)

            lock (lock) {
                cached = when (r) {
                    is UpdateResult.Ok -> r.projectDescription
                    else               -> null
                }

                notifyCargoProjectUpdate(r)
            }

            when (r) {
                is UpdateResult.Ok  -> LOG.info("Project '${module.project.name}' successfully updated")
                is UpdateResult.Err -> LOG.info("Project '${module.project.name}' update failed", r.error)
            }
        }
    }

    /**
     * Project-update task's notifier
     */
    inner class Notifier : CargoProjectWorkspaceListener {

        override fun onWorkspaceUpdateCompleted(r: UpdateResult) {
            when (r) {
                is UpdateResult.Ok  -> showBalloon("Project '${module.project.name}' successfully updated!", MessageType.INFO)
                is UpdateResult.Err -> showBalloon("Project '${module.project.name}' update failed: ${r.error.message}", MessageType.ERROR)
            }
        }

        private fun showBalloon(message: String, type: MessageType) {
            PopupUtil.showBalloonForActiveComponent(message, type)
        }
    }

    /**
     * IDEA's project model updater
     */
    inner class ProjectModelUpdater : CargoProjectWorkspaceListener {

        override fun onWorkspaceUpdateCompleted(r: UpdateResult) {
            when (r) {
                is UpdateResult.Ok ->
                    ApplicationManager.getApplication().runWriteAction {
                        if (!module.isDisposed) {
                            val libraryRoots =
                                r.projectDescription.packages
                                    .filter { !it.isModule }
                                    .mapNotNull { it.virtualFile }

                            module.updateLibrary(module.cargoLibraryName, libraryRoots)
                        }
                    }
            }
        }
    }

    /**
     * File changes listener, detecting changes inside the `Cargo.toml` files
     */
    inner class FileChangesWatcher : BulkFileListener {

        override fun before(events: MutableList<out VFileEvent>) {}

        override fun after(events: MutableList<out VFileEvent>) {
            if (!module.project.rustSettings.autoUpdateEnabled) return
            val toolchain = module.toolchain ?: return

            val needsUpdate = events.any {
                val file = it.file ?: return@any false
                file.name == RustToolchain.CARGO_TOML && ModuleUtilCore.findModuleForFile(file, module.project) == module
            }

            if (needsUpdate) {
                requestUpdateUsing(toolchain)
            }
        }
    }

    private inline fun <reified L> subscribeTo(t: Topic<L>, listener: L) {
        module.messageBus.connect().subscribe(t, listener!!)
    }

    @TestOnly
    fun setState(projectDescription: CargoProjectDescription) {
        lock (lock) {
            cached = projectDescription
            notifyCargoProjectUpdate(UpdateResult.Ok(projectDescription))
        }
    }
}

