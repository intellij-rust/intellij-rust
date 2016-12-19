package org.rust.cargo.project.workspace.impl

import backcompat.runWriteAction
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleComponent
import com.intellij.openapi.progress.BackgroundTaskQueue
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
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.project.workspace.CargoProjectWorkspace.UpdateResult
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.cargoLibraryName
import org.rust.cargo.util.cargoProjectRoot
import org.rust.cargo.util.updateLibrary
import org.rust.ide.notifications.showBalloon

private val LOG = Logger.getInstance(CargoProjectWorkspaceImpl::class.java)


class CargoProjectWorkspaceImpl(private val module: Module) : CargoProjectWorkspace, ModuleComponent {
    // First updates go through [debouncer] to be properly throttled,
    // and then via [taskQueue] to be serialized (it should be safe to execute
    // several Cargo's concurrently, but let's avoid that)
    private val debouncer = Debouncer(delayMillis = 1000, parentDisposable = module)
    private val taskQueue = BackgroundTaskQueue(module.project, "Cargo update")

    /**
     * Cached instance of the latest [CargoProjectDescription] instance synced with `Cargo.toml`
     *
     * NOTA BENE: It inherently may be null, since there may be no `Cargo.toml` present at all
     */
    private var cached: CargoProjectDescription? = null

    override fun getComponentName(): String = "org.rust.cargo.CargoProjectWorkspace"

    override fun initComponent() {
        module.messageBus
            .connect()
            .subscribe(VirtualFileManager.VFS_CHANGES, FileChangesWatcher())
    }

    override fun disposeComponent() {
        // Nothing to do here, bind all cleanup to `module`, which is Disposable
    }

    override fun projectClosed() {
    }

    override fun projectOpened() {
    }

    override fun moduleAdded() {
        val toolchain = module.project.toolchain ?: return
        requestImmediateUpdate(toolchain) { result ->
            when (result) {
                is UpdateResult.Err -> module.project.showBalloon(
                    "Project '${module.name}' failed to update.<br> ${result.error.message}", NotificationType.ERROR
                )
            }
        }
    }

    /**
     * Requests to updates Rust libraries asynchronously. Consecutive requests are coalesced.
     *
     * Works in two phases. First `cargo metadata` is executed on the background thread. Then,
     * the actual Library update happens on the event dispatch thread.
     */
    override fun requestUpdate(toolchain: RustToolchain) =
        requestUpdate(toolchain, null)

    override fun requestImmediateUpdate(toolchain: RustToolchain, afterCommit: (UpdateResult) -> Unit) =
        requestUpdate(toolchain, afterCommit)

    override fun syncUpdate(toolchain: RustToolchain) {
        taskQueue.run(UpdateTask(toolchain, module.cargoProjectRoot!!.path, null))
    }

    private fun requestUpdate(toolchain: RustToolchain, afterCommit: ((UpdateResult) -> Unit)?) {
        val contentRoot = module.cargoProjectRoot ?: return
        debouncer.submit({
            taskQueue.run(UpdateTask(toolchain, contentRoot.path, afterCommit))
        }, immediately = afterCommit != null)
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

        when (r) {
            is UpdateResult.Ok -> LOG.info("Project '${module.project.name}' successfully updated")
            is UpdateResult.Err -> LOG.info("Project '${module.project.name}' update failed", r.error)
        }
    }

    private fun updateModuleDependencies(projectDescription: CargoProjectDescription) {
        val libraryRoots = projectDescription.packages
            .filter { !it.isWorkspaceMember }
            .mapNotNull { it.contentRoot }

        module.updateLibrary(module.cargoLibraryName, libraryRoots)
    }

    private inner class UpdateTask(
        private val toolchain: RustToolchain,
        private val projectDirectory: String,
        private val afterCommit: ((UpdateResult) -> Unit)? = null
    ) : Task.Backgroundable(module.project, "Updating cargo") {

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
            afterCommit?.invoke(r)
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
            if (events.any(::isInterestingEvent)) {
                requestUpdate(toolchain)
            }
        }
    }

    @TestOnly
    fun setState(projectDescription: CargoProjectDescription) {
        commitUpdate(UpdateResult.Ok(projectDescription))
    }
}

/**
 * Executes tasks with rate of at most once every [delayMillis].
 */
private class Debouncer(
    private val delayMillis: Int,
    parentDisposable: Disposable
) {
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable)

    fun submit(task: () -> Unit, immediately: Boolean) = onAlarmThread {
        alarm.cancelAllRequests()
        if (immediately) {
            task()
        } else {
            alarm.addRequest(task, delayMillis)
        }
    }

    private fun onAlarmThread(work: () -> Unit) = alarm.addRequest(work, 0)
}
