/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

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
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
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
import org.rust.utils.pathAsPath
import java.nio.file.Path


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
private class WorkspaceMerger(private val updateCallback: () -> Unit) {
    private var rawWorkspace: CargoWorkspace? = null
    private var stdlib: StandardLibrary? = null

    fun setStdlib(lib: StandardLibrary) {
        checkWriteAccessAllowed()
        stdlib = lib
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
        workspace = raw.withStdlib(stdlib?.crates.orEmpty())
        updateCallback()
    }
}


class CargoProjectWorkspaceServiceImpl(private val module: Module) : CargoProjectWorkspaceService {
    // First updates go through [debouncer] to be properly throttled,
    // and then via [taskQueue] to be serialized (it should be safe to execute
    // several Cargo's concurrently, but let's avoid that)
    private val debouncer = Debouncer(delayMillis = 1000, parentDisposable = module)
    private val taskQueue = BackgroundTaskQueue(module.project, "Cargo update")

    private val workspaceMerger = WorkspaceMerger {
        ProjectRootManagerEx.getInstanceEx(module.project).makeRootsChange(EmptyRunnable.getInstance(), false, true)
    }

    init {
        fun refreshStdlib() {
            val projectDirectory = module.cargoProjectRoot?.pathAsPath
                ?: return
            val rustup = module.project.toolchain?.rustup(projectDirectory)
            if (rustup != null) {
                taskQueue.run(SetupRustStdlibTask(module, rustup, {
                    runWriteAction { workspaceMerger.setStdlib(it) }
                }))
            } else {
                ApplicationManager.getApplication().invokeLater {
                    val lib = StandardLibrary.fromPath(module.project.rustSettings.explicitPathToStdlib ?: "")
                    if (lib != null) {
                        runWriteAction { workspaceMerger.setStdlib(lib) }
                    }
                }
            }
        }

        with(module.messageBus.connect()) {
            subscribe(VirtualFileManager.VFS_CHANGES, CargoTomlWatcher(fun() {
                if (!module.project.rustSettings.autoUpdateEnabled) return
                val toolchain = module.project.toolchain ?: return
                requestUpdate(toolchain)
            }))

            subscribe(RustProjectSettingsService.TOOLCHAIN_TOPIC, object : RustProjectSettingsService.ToolchainListener {
                override fun toolchainChanged() {
                    val toolchain = module.project.toolchain
                    if (toolchain != null) {
                        requestUpdate(toolchain) { refreshStdlib() }
                    }
                }
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

                    is UpdateResult.Ok -> {
                        val outsider = result.workspace.packages
                            .filter { it.origin == PackageOrigin.WORKSPACE }
                            .mapNotNull { it.contentRoot }
                            .find { it !in module.moduleContentScope }

                        if (outsider != null) {
                            module.project.showBalloon(
                                "Workspace member ${outsider.presentableUrl} is outside of IDE project, " +
                                    "please open the root of the workspace.",
                                NotificationType.WARNING
                            )
                        }
                    }
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

    override fun syncUpdate(toolchain: RustToolchain) {
        taskQueue.run(UpdateTask(toolchain, module.cargoProjectRoot!!.pathAsPath, null))
    }

    private fun requestUpdate(toolchain: RustToolchain, afterCommit: ((UpdateResult) -> Unit)?) {
        val contentRoot = module.cargoProjectRoot ?: return
        debouncer.submit({
            taskQueue.run(UpdateTask(toolchain, contentRoot.pathAsPath, afterCommit))
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
                workspaceMerger.setRawWorkspace(r.workspace)
            }
        }
    }

    private inner class UpdateTask(
        private val toolchain: RustToolchain,
        private val projectDirectory: Path,
        private val afterCommit: ((UpdateResult) -> Unit)? = null
    ) : Task.Backgroundable(module.project, "Updating cargo") {

        private var result: UpdateResult? = null

        override fun run(indicator: ProgressIndicator) {
            if (module.isDisposed) return
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
    fun setRawWorkspace(workspace: CargoWorkspace) {
        commitUpdate(UpdateResult.Ok(workspace))
    }

    @TestOnly
    fun setStdlib(libs: StandardLibrary) {
        workspaceMerger.setStdlib(libs)
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

    fun submit(task: () -> Unit, immediately: Boolean) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            check(ApplicationManager.getApplication().isDispatchThread) {
                "Background activity in unit tests can lead to deadlocks"
            }
            task()
            return
        }
        onAlarmThread {
            alarm.cancelAllRequests()
            if (immediately) {
                task()
            } else {
                alarm.addRequest(task, delayMillis)
            }
        }
    }

    private fun onAlarmThread(work: () -> Unit) = alarm.addRequest(work, 0)
}
