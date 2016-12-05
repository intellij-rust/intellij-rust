package org.rust.cargo.project.workspace.impl

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleComponent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.Alarm
import com.intellij.util.PathUtil
import com.intellij.util.containers.SmartHashSet
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.project.workspace.CargoProjectWorkspaceListener
import org.rust.cargo.project.workspace.CargoProjectWorkspaceListener.UpdateResult
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.cargoLibraryName
import org.rust.cargo.util.cargoProjectRoot
import org.rust.cargo.util.updateLibrary
import org.rust.ide.notifications.showBalloon
import org.rust.ide.notifications.subscribeForOneMessage
import org.rust.ide.utils.EdtOnly
import org.rust.ide.utils.runWriteAction


/**
 * [CargoProjectWorkspace] component implementation.
 *
 * We don't want to invoke several instances of the `cargo metadata`
 * at the same time. In theory Cargo should handle this just fine, but
 * in practice it may deadlock and its just wasteful. So we use [alarm]
 * to debounce successive update requests and postpone updates when there
 * is one in progress already ([wantsUpdate], [pendingUpdates]).
 *
 * TODO: schedule updates when `Cargo.toml` is being edited even if it is not saved yet.
 */
class CargoProjectWorkspaceImpl(private val module: Module) : CargoProjectWorkspace, ModuleComponent {

    private val DELAY = 1000 /* milliseconds */

    private val LOG = Logger.getInstance(CargoProjectWorkspaceImpl::class.java)

    /**
     * Alarm used to coalesce consecutive update requests.
     * It uses a pooled thread. Actual updates happen on EDT thread.
     */
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, module)
    private var wantsUpdate by EdtOnly(false)
    private val pendingUpdates by EdtOnly(SmartHashSet<UpdateTask>())

    /**
     * Cached instance of the latest [CargoProjectDescription] instance synced with `Cargo.toml`
     *
     * NOTA BENE: It inherently may be null, since there may be no `Cargo.toml` present at all
     */
    private var cached: CargoProjectDescription? = null

    /** Component hooks */

    override fun getComponentName(): String = "org.rust.cargo.CargoProjectWorkspace"

    override fun initComponent() {
        module.messageBus
            .connect()
            .subscribe(VirtualFileManager.VFS_CHANGES, FileChangesWatcher())
    }

    override fun disposeComponent() {
    }

    override fun projectClosed() { /* NOP */ }

    override fun projectOpened() { /* NOP */ }

    override fun moduleAdded() {
        module.project.toolchain?.let { toolchain ->
            subscribeForOneMessage(module.messageBus, CargoProjectWorkspaceListener.Topics.UPDATES, object : CargoProjectWorkspaceListener {
                override fun onWorkspaceUpdateCompleted(r: UpdateResult) {
                    when (r) {
                        is UpdateResult.Err -> module.project.showBalloon(
                            "Project '${module.name}' failed to update.<br> ${r.error.message}", NotificationType.ERROR)
                    }
                }
            })

            requestUpdateUsing(toolchain, immediately = true)
        }
    }

    /**
     * Requests to updates Rust libraries asynchronously. Consecutive requests are coalesced.
     *
     * Works in two phases. First `cargo metadata` is executed on the background thread. Then,
     * the actual Library update happens on the event dispatch thread.
     */
    override fun requestUpdateUsing(toolchain: RustToolchain, immediately: Boolean) {
        val contentRoot = module.cargoProjectRoot ?: return
        val task = UpdateTask(toolchain, contentRoot.path)

        alarm.cancelAllRequests()

        if (immediately)
            task.queueIfNeeded()
        else
            alarm.addRequest({ task.queueIfNeeded() }, DELAY)
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
        get() {
            check(ApplicationManager.getApplication().isReadAccessAllowed)
            return cached
        }

    private fun commitUpdate(r: UpdateResult) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (module.isDisposed) return

        if (r is UpdateResult.Ok) {
            runWriteAction {
                cached = r.projectDescription
                updateModuleDependencies(r.projectDescription)
            }
        }

        notifyCargoProjectUpdate(r)

        when (r) {
            is UpdateResult.Ok  -> LOG.info("Project '${module.project.name}' successfully updated")
            is UpdateResult.Err -> LOG.info("Project '${module.project.name}' update failed", r.error)
        }
    }

    private fun updateModuleDependencies(projectDescription: CargoProjectDescription) {
        val libraryRoots =
            projectDescription.packages
                .filter { !it.isModule }
                .mapNotNull { it.contentRoot }

        module.updateLibrary(module.cargoLibraryName, libraryRoots)
    }

    private fun notifyCargoProjectUpdate(r: UpdateResult) {
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
                result = UpdateResult.Err(ExecutionException("Invalid toolchain ${toolchain.presentableLocation}"))
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
            commitUpdate(r)
            onDone()
        }

        override fun onCancel() {
            onDone()
        }

        // Backward compat: no `onFinished` in IDEA 15
        fun onDone() {
            pendingUpdates.remove(this)
            if (wantsUpdate) {
                wantsUpdate = false
                requestUpdateUsing(toolchain)
            }
        }

        fun queueIfNeeded() {
            ApplicationManager.getApplication().invokeAndWait({
                LOG.assertTrue(pendingUpdates.size <= 1)
                if (pendingUpdates.isEmpty) {
                    pendingUpdates += this
                    queue()
                } else {
                    wantsUpdate = true
                }
            }, ModalityState.NON_MODAL)
        }
    }

    /**
     * File changes listener, detecting changes inside the `Cargo.toml` files
     */
    inner class FileChangesWatcher : BulkFileListener {
        private val IMPLICIT_TARGET_DIRS = listOf(
            CargoConstants.ProjectLayout.binaries,
            CargoConstants.ProjectLayout.sources,
            CargoConstants.ProjectLayout.tests
        ).flatten()

        override fun before(events: List<VFileEvent>) {
        }

        override fun after(events: List<VFileEvent>) {
            fun isInterestingEvent(event: VFileEvent): Boolean =
                event.path.endsWith(RustToolchain.CARGO_TOML) ||
                    IMPLICIT_TARGET_DIRS.any {
                        PathUtil.getParentPath(event.path).endsWith(it)
                    } && (event !is VFileContentChangeEvent) && (event !is VFilePropertyChangeEvent)


            if (!module.project.rustSettings.autoUpdateEnabled) return
            val toolchain = module.project.toolchain ?: return
            if (events.any { isInterestingEvent(it) }) {
                requestUpdateUsing(toolchain)
            }
        }
    }

    @TestOnly
    fun setState(projectDescription: CargoProjectDescription) {
        commitUpdate(UpdateResult.Ok(projectDescription))
    }
}
