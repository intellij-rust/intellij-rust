package org.rust.cargo.project.workspace.impl

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.BackgroundTaskQueue
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.Alarm
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.*
import org.rust.cargo.project.workspace.CargoProjectWorkspaceService.UpdateResult
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.cargoProjectRoot
import org.rust.ide.notifications.showBalloon
import org.rust.ide.utils.checkReadAccessAllowed
import org.rust.ide.utils.checkWriteAccessAllowed


private val LOG = Logger.getInstance(CargoProjectWorkspaceServiceImpl::class.java)


/**
 * [CargoWorkspace] of a real project consists of two pieces:
 *
 *   * Project structure reported by `cargo metadata`
 *   * Standard library, usually retrieved from rustup.
 *
 * This two piece may vary independently. [WorkspaceMerger]
 * merges them into a single [CargoWorkspace]. It's executed
 * only on EDT, so you may think of it as an actor maintaining
 * a two bits of state. 
 */
private class WorkspaceMerger {
    private var rawWorkspace: CargoWorkspace? = null
    private var stdlib: List<StandardLibraryRoots.StdCrate> = emptyList()

    fun setStdlib(libs: List<StandardLibraryRoots.StdCrate>) {
        checkWriteAccessAllowed()
        stdlib = libs
        update()
    }

    fun setRawWorkspace(workspace: CargoWorkspace) {
        checkWriteAccessAllowed()
        rawWorkspace = workspace
        update()
    }

    var workspace: CargoWorkspace? = null
        get() {
            checkReadAccessAllowed()
            return field
        }
        private set(value) {
            field = value
        }

    private fun update() {
        val raw = rawWorkspace
        if (raw == null) {
            workspace = null
            return
        }
        workspace = raw.withStdlib(stdlib)
    }
}


class CargoProjectWorkspaceServiceImpl(private val module: Module) : CargoProjectWorkspaceService {
    // First updates go through [debouncer] to be properly throttled,
    // and then via [taskQueue] to be serialized (it should be safe to execute
    // several Cargo's concurrently, but let's avoid that)
    private val debouncer = Debouncer(delayMillis = 1000, parentDisposable = module)
    private val taskQueue = BackgroundTaskQueue(module.project, "Cargo update")

    private val workspaceMerger = WorkspaceMerger()

    init {
        fun refreshStdlib() {
            val projectDirectory = module.cargoProjectRoot?.path
                ?: return
            val rustup = module.project.toolchain?.rustup(projectDirectory)
                ?: return

            SetupRustStdlibTask(module, rustup).queue()
        }

        with(module.messageBus.connect()) {
            subscribe(VirtualFileManager.VFS_CHANGES, CargoTomlWatcher(fun() {
                if (!module.project.rustSettings.autoUpdateEnabled) return
                val toolchain = module.project.toolchain ?: return
                requestUpdate(toolchain)
            }))

            subscribe(RustProjectSettingsService.TOOLCHAIN_TOPIC, object : RustProjectSettingsService.ToolchainListener {
                override fun toolchainChanged(newToolchain: RustToolchain?) = refreshStdlib()
            })
        }

        refreshStdlib()

        val toolchain = module.project.toolchain
        if (toolchain != null) {
            requestImmediateUpdate(toolchain) { result ->
                when (result) {
                    is UpdateResult.Err -> module.project.showBalloon(
                        "Project '${module.name}' failed to update.<br> ${result.error.message}", NotificationType.ERROR
                    )
                }
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

    override fun setStandardLibrary(stdlib: List<StandardLibraryRoots.StdCrate>) {
        checkWriteAccessAllowed()
        workspaceMerger.setStdlib(stdlib)
    }

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
     * NOTA BENE: [CargoProjectWorkspaceService] is rather low-level abstraction around, `Cargo.toml`-backed projects
     *            mapping underpinning state of the `Cargo.toml` workspace _transparently_, i.e. it doesn't provide
     *            any facade atop of the latter insulating itself from inconsistencies in the underlying layer. For
     *            example, [CargoProjectWorkspaceService] wouldn't be able to provide a valid [CargoWorkspace] instance
     *            until the `Cargo.toml` becomes sound
     */
    override val workspace: CargoWorkspace? get() = workspaceMerger.workspace

    private fun commitUpdate(r: UpdateResult) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (module.isDisposed) return

        if (r is UpdateResult.Ok) {
            runWriteAction {
                updateModuleDependencies(r.workspace)
                workspaceMerger.setRawWorkspace(r.workspace)
            }
        }

        when (r) {
            is UpdateResult.Ok -> LOG.info("Project '${module.project.name}' successfully updated")
            is UpdateResult.Err -> LOG.info("Project '${module.project.name}' update failed", r.error)
        }
    }

    private fun updateModuleDependencies(workspace: CargoWorkspace) {
        val libraryRoots = workspace.packages
            .filter { it.origin != PackageOrigin.WORKSPACE }
            .mapNotNull { it.contentRoot?.url }

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
            indicator.isIndeterminate = true

            if (!toolchain.looksLikeValidToolchain()) {
                result = UpdateResult.Err(ExecutionException("Invalid toolchain ${toolchain.presentableLocation}"))
                return
            }

            val cargo = toolchain.cargo(projectDirectory)
            result = try {
                val description = cargo.fullProjectDescription(module, object : ProcessAdapter() {
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

    @TestOnly
    fun setState(workspace: CargoWorkspace) {
        commitUpdate(UpdateResult.Ok(workspace))
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
