package org.rust.cargo.project

import com.google.gson.Gson
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import org.apache.commons.lang.StringUtils
import org.rust.cargo.Cargo
import org.rust.cargo.project.model.CargoProjectInfo
import org.rust.cargo.project.settings.CargoExecutionSettings
import org.rust.cargo.service.CargoInstallationManager
import org.rust.cargo.util.Platform
import org.rust.cargo.project.RustSdkType
import org.rust.cargo.project.model.CargoPackageInfo
import org.rust.cargo.project.module.RustModuleType
import java.io.File
import java.util.*

class CargoProjectResolver : ExternalSystemProjectResolver<CargoExecutionSettings> {

    @Throws(ExternalSystemException::class, IllegalArgumentException::class, IllegalStateException::class)
    override fun resolveProjectInfo(id: ExternalSystemTaskId,
                                    projectPath: String,
                                    isPreviewMode: Boolean,
                                    settings: CargoExecutionSettings?,
                                    listener: ExternalSystemTaskNotificationListener): DataNode<ProjectData>? {

        listener.onStatusChange(ExternalSystemTaskNotificationEvent(id, "Resolving dependencies..."))

        val data: CargoProjectInfo
        try {
            val processOut =
                Platform.runExecutableWith(
                    settings!!.cargoExecutable,
                    arrayListOf(
                        RustSdkType.CARGO_METADATA_SUBCOMMAND,
                        "--output-format",  "json",
                        "--manifest-path",  File(projectPath, Cargo.BUILD_FILE).absolutePath,
                        "--features",       StringUtils.join(settings.features, ",")
                    ),
                    object : ProcessAdapter() {
                        override fun onTextAvailable(event: ProcessEvent, outputType: Key<Any>) {
                            val text = event.text.trim { it <= ' ' }
                            if (text.startsWith("Updating") || text.startsWith("Downloading")) {
                                listener.onStatusChange(ExternalSystemTaskNotificationEvent(id, text))
                            } else {
                                listener.onTaskOutput(id, text, outputType === ProcessOutputTypes.STDOUT)
                            }
                        }
                    })

            if (processOut.exitCode != 0) {
                //
                // NOTE:
                //  Since `metadata` isn't made its way into Cargo bundle (yet),
                //  this particular check verifies whether user has it installed already or not.
                //  Hopefully based on the following lines
                //
                //  https://github.com/rust-lang/cargo/blob/master/src/bin/cargo.rs#L189 (`execute_subcommand`)
                //
                throw ExternalSystemException("Failed to execute cargo: " + processOut.stderr)
            }

            data = Gson().fromJson(
                processOut.stdout.dropWhile { c -> c != '{' },
                CargoProjectInfo::class.java
            )

        } catch (e: ExecutionException) {
            throw ExternalSystemException(e)
        }

        val projectNode =
            DataNode(
                ProjectKeys.PROJECT,
                ProjectData(CargoProjectSystem.ID, data.root.name, projectPath, projectPath),
                null
            )

        val projectRoot = File(projectPath)

        // TODO(winger, kudinkin): properly handle versions
        val moduleOrLibrary = HashMap<String, DataNode<*>>()

        for (pkg in data.packages) {
            val root = File(pkg.manifest_path).parentFile

            if (VfsUtil.isAncestor(projectRoot, root, /* strict = */ false)) {
                // Add as a module
                val modData =
                    ModuleData(
                        pkg.name,
                        CargoProjectSystem.ID,
                        RustModuleType.MODULE_TYPE_ID,
                        pkg.name,
                        root.absolutePath,
                        root.absolutePath
                    )

                val moduleNode = projectNode.createChild(ProjectKeys.MODULE, modData)

                moduleOrLibrary.put(pkg.name, moduleNode)

                // Publish source-/test-/resources- roots
                addContentRoots(moduleNode, root, pkg)

            } else {
                // Add as a library
                val libData = LibraryData(CargoProjectSystem.ID, "${pkg.name} ${pkg.version}")

                libData.addPath(LibraryPathType.BINARY, root.absolutePath)
                libData.addPath(LibraryPathType.SOURCE, root.absolutePath)

                moduleOrLibrary.put(
                    pkg.name,
                    projectNode.createChild(ProjectKeys.LIBRARY, libData)
                )
            }
        }

        // TODO(winger, kudinkin): add transitive dependencies too?

        // Add dependencies
        for (pkg in data.packages) {
            val pkgNode = moduleOrLibrary[pkg.name]!!

            if (pkgNode.key != ProjectKeys.MODULE) {
                // Skip dependencies for libraries
                continue
            }

            for (dep in pkg.dependencies) {
                val depNode = moduleOrLibrary[dep.name] ?: continue

                if (depNode.key == ProjectKeys.MODULE) {
                    pkgNode.createChild(
                        ProjectKeys.MODULE_DEPENDENCY,
                        ModuleDependencyData(
                            pkgNode.data as ModuleData,
                            depNode.data as ModuleData
                        )
                    )
                } else if (depNode.key == ProjectKeys.LIBRARY) {
                    pkgNode.createChild(
                        ProjectKeys.LIBRARY_DEPENDENCY,
                        LibraryDependencyData(
                                pkgNode.data as ModuleData,
                                depNode.data as LibraryData,
                                LibraryLevel.PROJECT
                        )
                    )
                } else {
                    throw AssertionError("Panic! You may not reach this point!")
                }
            }
        }

        return projectNode
    }

    private fun addContentRoots(node: DataNode<ModuleData>, root: File, pkg: CargoPackageInfo) {
        val content = ContentRootData(CargoProjectSystem.ID, root.absolutePath)

        //
        // TODO(kudinkin):  Align with established Cargo's layout
        //                  http://doc.crates.io/manifest.html#the-project-layout
        //
        for (t in pkg.targets)
            content.storePath(ExternalSystemSourceType.SOURCE, File(t.src_path).parentFile.absolutePath)

        node.createChild(ProjectKeys.CONTENT_ROOT, content)
    }

    override fun cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean {
        // TODO(kudinkin): cancel properly
        return false
    }
}
