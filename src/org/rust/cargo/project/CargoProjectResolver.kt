package org.rust.cargo.project

import com.google.gson.Gson
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
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
import org.rust.lang.module.RustModuleType
import java.io.File
import java.util.*

class CargoProjectResolver : ExternalSystemProjectResolver<CargoExecutionSettings> {
    @Throws(ExternalSystemException::class, IllegalArgumentException::class, IllegalStateException::class)
    override fun resolveProjectInfo(
            id: ExternalSystemTaskId,
            projectPath: String,
            isPreviewMode: Boolean,
            settings: CargoExecutionSettings?,
            listener: ExternalSystemTaskNotificationListener): DataNode<ProjectData>? {
        listener.onStatusChange(ExternalSystemTaskNotificationEvent(id, "Resolving dependencies..."))
        val data: CargoProjectInfo
        try {
            val cmd = GeneralCommandLine()
            cmd.exePath = settings!!.cargoExecutable
            cmd.addParameter("metadata")
            cmd.addParameters("--output-format", "json")
            cmd.addParameters("--manifest-path", File(projectPath, Cargo.BUILD_FILE).absolutePath)
            cmd.addParameters("--features", StringUtils.join(settings.features, ","))
            val process = cmd.createProcess()
            val handler = CapturingProcessHandler(process)
            handler.addProcessListener(
                    object : ProcessAdapter() {
                        override fun onTextAvailable(event: ProcessEvent, outputType: Key<Any>) {
                            if (event.text.startsWith("    Updating") || event.text.startsWith(" Downloading")) {
                                listener.onStatusChange(ExternalSystemTaskNotificationEvent(id, event.text.trim { it <= ' ' }))
                            } else {
                                listener.onTaskOutput(id, event.text, outputType === ProcessOutputTypes.STDOUT)
                            }
                        }
                    })
            val output = handler.runProcess()
            if (output.exitCode != 0) {
                throw ExternalSystemException("Failed to execute cargo: " + output.stderr)
            }
            data = Gson().fromJson(output.stdout.dropWhile { c -> c != '{' }, CargoProjectInfo::class.java)
        } catch (e: ExecutionException) {
            throw ExternalSystemException(e)
        }

        val projectNode = DataNode(
                ProjectKeys.PROJECT,
                ProjectData(CargoProjectSystem.ID, data.root.name, projectPath, projectPath),
                null)
        val projectRoot = File(projectPath)
        // TODO properly handle versions
        val moduleOrLibrary = HashMap<String, DataNode<*>>()
        for (pkg in data.packages) {
            val packageRoot = File(pkg.manifest_path).parentFile
            if (VfsUtil.isAncestor(projectRoot, packageRoot, false)) {
                // Create module
                val moduleData = ModuleData(
                        pkg.name,
                        CargoProjectSystem.ID,
                        RustModuleType.RUST_MODULE,
                        pkg.name,
                        packageRoot.absolutePath,
                        packageRoot.absolutePath)
                val moduleNode = projectNode.createChild(ProjectKeys.MODULE, moduleData)
                moduleOrLibrary.put(pkg.name, moduleNode)
                addSourceRoot(moduleNode, packageRoot.absolutePath)
            } else {
                // Create library
                val libraryData = LibraryData(CargoProjectSystem.ID, pkg.name + " " + pkg.version)
                libraryData.addPath(LibraryPathType.BINARY, packageRoot.absolutePath)
                libraryData.addPath(LibraryPathType.SOURCE, packageRoot.absolutePath)
                val libraryNode = projectNode.createChild(ProjectKeys.LIBRARY, libraryData)
                moduleOrLibrary.put(pkg.name, libraryNode)
            }
        }
        // Add dependencies
        // TODO add transitive dependencies too
        for (pkg in data.packages) {
            val pkgNode = moduleOrLibrary[pkg.name]!!
            if (pkgNode.key != ProjectKeys.MODULE) {
                // Skip dependencies for libraries
                continue
            }
            for (dependency in pkg.dependencies) {
                val dependencyNode = moduleOrLibrary[dependency.name]!!
                if (dependencyNode.key == ProjectKeys.MODULE) {
                    pkgNode.createChild(
                            ProjectKeys.MODULE_DEPENDENCY,
                            ModuleDependencyData(
                                    pkgNode.data as ModuleData,
                                    dependencyNode.data as ModuleData))
                } else if (dependencyNode.key == ProjectKeys.LIBRARY) {
                    pkgNode.createChild(
                            ProjectKeys.LIBRARY_DEPENDENCY,
                            LibraryDependencyData(
                                    pkgNode.data as ModuleData,
                                    dependencyNode.data as LibraryData,
                                    LibraryLevel.PROJECT))
                } else {
                    throw AssertionError("unreachable")
                }
            }
        }
        return projectNode
    }

    private fun addSourceRoot(node: DataNode<ModuleData>, path: String) {
        node.createChild(ProjectKeys.CONTENT_ROOT, ContentRootData(CargoProjectSystem.ID, path))
    }

    override fun cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean {
        //TODO proper cancellation
        return false
    }
}
