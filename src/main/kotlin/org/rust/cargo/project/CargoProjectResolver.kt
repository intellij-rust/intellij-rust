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
import org.rust.cargo.CargoConstants
import org.rust.cargo.commands.Cargo
import org.rust.cargo.CargoProjectDescription
import org.rust.cargo.project.module.RustModuleType
import org.rust.cargo.project.module.persistence.CargoModuleData
import org.rust.cargo.project.module.persistence.ExternCrateData
import org.rust.cargo.project.settings.CargoExecutionSettings
import java.io.File

class CargoProjectResolver : ExternalSystemProjectResolver<CargoExecutionSettings> {

    @Throws(ExternalSystemException::class, IllegalArgumentException::class, IllegalStateException::class)
    override fun resolveProjectInfo(id: ExternalSystemTaskId,
                                    projectPath: String,
                                    isPreviewMode: Boolean,
                                    settings: CargoExecutionSettings?,
                                    listener: ExternalSystemTaskNotificationListener): DataNode<ProjectData>? {

        val pathToCargo = settings?.cargoPath ?: return null
        val metadata = readProjectDescription(id, listener, projectPath, pathToCargo)

        val projectNode =
            DataNode(
                ProjectKeys.PROJECT,
                ProjectData(CargoProjectSystem.ID, metadata.projectName, projectPath, projectPath),
                null
            )

        val modules = metadata.modules.associate { it to createModuleNode(it, projectNode) }
        val libraries = metadata.libraries.associate { it to createLibraryNode(it, projectNode) }

        addDependencies(modules, libraries)

        return projectNode
    }

    private fun addDependencies(modules: Map<CargoProjectDescription.Package, DataNode<ModuleData>>,
                                libraries: Map<CargoProjectDescription.Package, DataNode<LibraryData>>) {
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
                                       pathToCargo: String): CargoProjectDescription {

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
           Cargo(pathToCargo, projectPath).fullProjectDescription(cargoListener)
        } catch(e: ExecutionException) {
            throw ExternalSystemException(e)
        }
    }

    private fun createModuleNode(module: CargoProjectDescription.Package, projectNode: DataNode<ProjectData>): DataNode<ModuleData> {
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

        moduleNode.addRoots(module)
        moduleNode.addCargoData(module)

        return moduleNode
    }

    private fun createLibraryNode(lib: CargoProjectDescription.Package, projectNode: DataNode<ProjectData>): DataNode<LibraryData> {
        val libData = LibraryData(CargoProjectSystem.ID, "${lib.name} ${lib.version}")
        libData.addPath(LibraryPathType.SOURCE, lib.contentRoot.absolutePath)
        val libNode = projectNode.createChild(ProjectKeys.LIBRARY, libData)
        return libNode
    }
}

private fun DataNode<ModuleData>.addRoots(module: CargoProjectDescription.Package) {
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

    createChild(ProjectKeys.CONTENT_ROOT, content)
}

private fun DataNode<ModuleData>.addCargoData(module: CargoProjectDescription.Package) {
    check(module.isModule)
    val externCrates = module.dependencies.mapNotNull { it.asExternCrateFor(module) }
    createChild(CargoConstants.KEYS.CARGO_MODULE_DATA, CargoModuleData(
        module.targets,
        externCrates
    ))
}

private val CargoProjectDescription.modules: Collection<CargoProjectDescription.Package> get() =
    packages.filter { it.isModule }

private val CargoProjectDescription.libraries: Collection<CargoProjectDescription.Package> get() =
    packages.filter { !it.isModule }

private val CargoProjectDescription.Package.moduleDependencies: Collection<CargoProjectDescription.Package> get() =
    dependencies.filter { it.isModule }

private val CargoProjectDescription.Package.libraryDependencies: Collection<CargoProjectDescription.Package> get() =
    dependencies.filter { !it.isModule }

private val CargoProjectDescription.Package.libTarget: CargoProjectDescription.Target? get() =
    targets.find { it.isLib }

private fun CargoProjectDescription.Package.asExternCrateFor(module: CargoProjectDescription.Package): ExternCrateData? {
    check(module.isModule)
    val target = libTarget ?: return null

    val absPath = File(contentRoot, target.path).absolutePath
    val path = if (isModule)
        File(absPath).relativeTo(module.contentRoot).path
    else
        absPath

    return ExternCrateData(name, path)
}
