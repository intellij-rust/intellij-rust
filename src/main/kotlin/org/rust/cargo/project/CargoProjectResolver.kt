package org.rust.cargo.project

import com.intellij.execution.ExecutionException
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
import org.rust.cargo.commands.Cargo
import org.rust.cargo.commands.CargoProjectDescription
import org.rust.cargo.project.module.RustModuleType
import org.rust.cargo.project.settings.CargoExecutionSettings
import java.io.File

class CargoProjectResolver : ExternalSystemProjectResolver<CargoExecutionSettings> {

    @Throws(ExternalSystemException::class, IllegalArgumentException::class, IllegalStateException::class)
    override fun resolveProjectInfo(id: ExternalSystemTaskId,
                                    projectPath: String,
                                    isPreviewMode: Boolean,
                                    settings: CargoExecutionSettings?,
                                    listener: ExternalSystemTaskNotificationListener): DataNode<ProjectData>? {

        val metadata = readProjectDescription(id, listener, projectPath, settings)

        val projectNode =
            DataNode(
                ProjectKeys.PROJECT,
                ProjectData(CargoProjectSystem.ID, metadata.projectName, projectPath, projectPath),
                null
            )

        val modules = metadata.modules.toMap { it to createModuleNode(it, projectNode) }
        val libraries = metadata.libraries.toMap { it to createLibraryNode(it, projectNode) }

        addDependencies(modules, libraries)

        return projectNode
    }

    private fun addDependencies(modules: Map<CargoProjectDescription.Module, DataNode<ModuleData>>,
                                libraries: Map<CargoProjectDescription.Library, DataNode<LibraryData>>) {
        for ((module, node) in modules) {
            for (dep in module.moduleDependencies) {
                node.createChild(
                    ProjectKeys.MODULE_DEPENDENCY,
                    ModuleDependencyData(
                        node.data,
                        modules[dep]!!.data
                    )
                )
            }

            for (dep in module.libraryDependencies) {
                node.createChild(
                    ProjectKeys.LIBRARY_DEPENDENCY,
                    LibraryDependencyData(
                        node.data,
                        libraries[dep]!!.data,
                        LibraryLevel.PROJECT
                    )
                )
            }
        }
    }

    override fun cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean {
        // TODO(kudinkin): cancel properly
        return false
    }

    private fun readProjectDescription(id: ExternalSystemTaskId,
                                       listener: ExternalSystemTaskNotificationListener,
                                       projectPath: String,
                                       settings: CargoExecutionSettings?): CargoProjectDescription {

        listener.onStatusChange(ExternalSystemTaskNotificationEvent(id, "Resolving dependencies..."))
        val cargoListener = object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<Any>) {
                val text = event.text.trim { it <= ' ' }
                if (text.startsWith("Updating") || text.startsWith("Downloading")) {
                    listener.onStatusChange(ExternalSystemTaskNotificationEvent(id, text))
                } else {
                    listener.onTaskOutput(id, text, outputType === ProcessOutputTypes.STDOUT)
                }
            }
        }

        return try {
           Cargo.fromProjectDirectory(settings!!.cargoPath, projectPath).fullProjectDescription(cargoListener)
        } catch(e: ExecutionException) {
            throw ExternalSystemException(e)
        }
    }

    private fun createModuleNode(module: CargoProjectDescription.Module, projectNode: DataNode<ProjectData>): DataNode<ModuleData> {
        val root = module.contentRoot.absolutePath
        val modData =
            ModuleData(
                module.name,
                CargoProjectSystem.ID,
                RustModuleType.MODULE_TYPE_ID,
                module.name,
                root,
                root
            )

        val moduleNode = projectNode.createChild(ProjectKeys.MODULE, modData)

        // Publish source-/test-/resources- roots
        val content = ContentRootData(CargoProjectSystem.ID, module.contentRoot.absolutePath)

        // Standard cargo layout
        // http://doc.crates.io/manifest.html#the-project-layout
        for (src in listOf("src", "examples")) {
            content.storePath(ExternalSystemSourceType.SOURCE, File(module.contentRoot, src).absolutePath)
        }

        for (test in listOf("tests", "benches")) {
            content.storePath(ExternalSystemSourceType.TEST, File(module.contentRoot, test).absolutePath)
        }

        content.storePath(ExternalSystemSourceType.EXCLUDED, File(module.contentRoot, "target").absolutePath)

        moduleNode.createChild(ProjectKeys.CONTENT_ROOT, content)
        return moduleNode
    }

    private fun createLibraryNode(lib: CargoProjectDescription.Library, projectNode: DataNode<ProjectData>): DataNode<LibraryData> {
        val libData = LibraryData(CargoProjectSystem.ID, "${lib.name} ${lib.version}")
        libData.addPath(LibraryPathType.SOURCE, lib.contentRoot.absolutePath)
        val libNode = projectNode.createChild(ProjectKeys.LIBRARY, libData)
        return libNode
    }
}
