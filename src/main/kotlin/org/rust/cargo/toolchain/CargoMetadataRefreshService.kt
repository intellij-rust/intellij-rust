import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import org.rust.cargo.CargoProjectDescription
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.toolchain

private val LOG = Logger.getInstance(CargoMetadataRefreshService::class.java);

/*
 * Uses `cargo metadata` command to update IDEA libraries and rust targets.
 *
 * All Cargo packages are stored in one library.
 */
class CargoMetadataRefreshService(private val module: Module) : BulkFileListener {
    override fun before(events: MutableList<out VFileEvent>) {
    }

    override fun after(events: MutableList<out VFileEvent>) {
        val toolchain = module.toolchain ?: return
        val needsUpdate = events.any {
            val file = it.file ?: return@any false
            file.name == "Cargo.toml" && ModuleUtilCore.findModuleForFile(file, module.project) == module
        }
        if (needsUpdate) {
            scheduleUpdate(toolchain)
        }
    }

    init {
        module.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, this)
    }

    private val alarm = Alarm()
    private val DELAY_MILLIS = 1000

    /*
     * Updates Rust libraries asynchronously. Consecutive updates are coalesced.
     *
     * Works in two phases. First `cargo metadata` is executed on the background thread. Then,
     * the actual Library update happens on the event dispatch thread.
     */
    fun scheduleUpdate(toolchain: RustToolchain) {
        val contentRoot = ModuleRootManager.getInstance(module).contentRoots.firstOrNull() ?: return
        if (contentRoot.findChild("Cargo.toml") == null) {
            return
        }

        val task = UpdateTask(module, toolchain, contentRoot.path)
        alarm.cancelAllRequests()
        alarm.addRequest({ task.queue() }, DELAY_MILLIS)
    }

    private class UpdateTask(
        val module: Module,
        private val toolchain: RustToolchain,
        private val projectDirectory: String
    ) : Task.Backgroundable(module.project, "Updating cargo") {

        private var result: Result? = null

        override fun run(indicator: ProgressIndicator) {
            LOG.info("Cargo project update started")
            result = try {
                val cargo = toolchain.cargo(projectDirectory)
                if (cargo == null) {
                    Result.Err(ExecutionException("Cargo not found"))
                } else {
                    val description = cargo.fullProjectDescription(object : ProcessAdapter() {
                        override fun onTextAvailable(event: ProcessEvent, outputType: Key<Any>) {
                            val text = event.text.trim { it <= ' ' }
                            if (text.startsWith("Updating") || text.startsWith("Downloading")) {
                                indicator.text = text
                            }
                        }
                    })

                    Result.Ok(description)
                }
            } catch (e: ExecutionException) {
                Result.Err(e)
            }
        }

        override fun onSuccess() {
            val result = requireNotNull(result)

            when (result) {
                is Result.Err -> LOG.info("Cargo project update failed", result.error)
                is Result.Ok  -> ApplicationManager.getApplication().runWriteAction {
                    updateLibraries(module, result.cargoProject)
                }
            }
        }

        private sealed class Result {
            class Ok(val cargoProject: CargoProjectDescription) : Result()
            class Err(val error: ExecutionException) : Result()
        }
    }
}

private fun updateLibraries(module: Module, cargoProject: CargoProjectDescription) {
    check(ApplicationManager.getApplication().isWriteAccessAllowed)
    if (module.isDisposed) return

    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(module.project)
    val cargoLibrary = libraryTable.getLibraryByName(module.cargoLibraryName)
        ?: libraryTable.createLibrary(module.cargoLibraryName)
        ?: return

    fillLibrary(cargoLibrary, cargoProject)

    ModuleRootModificationUtil.addDependency(module, cargoLibrary)
    LOG.info("Cargo project successfully updated")
}

fun fillLibrary(cargoLibrary: Library, cargoProject: CargoProjectDescription) {
    val fs = LocalFileSystem.getInstance()
    val model = cargoLibrary.modifiableModel
    for (url in cargoLibrary.getUrls(OrderRootType.CLASSES)) {
        model.removeRoot(url, OrderRootType.CLASSES)
    }

    for (pkg in cargoProject.packages.filter { !it.isModule }) {
        val root = fs.findFileByIoFile(pkg.contentRoot)
        if (root == null) {
            LOG.warn("Can't find root for ${pkg.name}")
            continue
        }
        model.addRoot(root, OrderRootType.CLASSES)
    }
    model.commit()
}

private val Module.cargoLibraryName: String get() = "Cargo <$name>"

