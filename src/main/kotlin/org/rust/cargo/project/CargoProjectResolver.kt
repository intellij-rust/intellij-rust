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
import org.apache.commons.lang.StringUtils
import org.rust.cargo.Cargo
import org.rust.cargo.project.settings.CargoExecutionSettings
import org.rust.cargo.util.PlatformUtil
import java.io.File

class CargoProjectResolver : ExternalSystemProjectResolver<CargoExecutionSettings> {

    @Throws(ExternalSystemException::class, IllegalArgumentException::class, IllegalStateException::class)
    override fun resolveProjectInfo(id: ExternalSystemTaskId,
                                    projectPath: String,
                                    isPreviewMode: Boolean,
                                    settings: CargoExecutionSettings?,
                                    listener: ExternalSystemTaskNotificationListener): DataNode<ProjectData>? {

        val metadata = readCargoMetadata(id, listener, projectPath, settings)

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

    private fun addDependencies(modules: Map<CargoMetadata.Module, DataNode<ModuleData>>,
                                libraries: Map<CargoMetadata.Library, DataNode<LibraryData>>) {
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

    private fun readCargoMetadata(id: ExternalSystemTaskId,
                                  listener: ExternalSystemTaskNotificationListener,
                                  projectPath: String,
                                  settings: CargoExecutionSettings?): CargoMetadata {

        listener.onStatusChange(ExternalSystemTaskNotificationEvent(id, "Resolving dependencies..."))

        val processOut = try {
            PlatformUtil.runExecutableWith(
                RustSdkType.CARGO_BINARY_NAME,
                arrayListOf(
                    RustSdkType.CARGO_METADATA_SUBCOMMAND,
                    "--manifest-path", File(projectPath, Cargo.BUILD_FILE).absolutePath,
                    "--features", StringUtils.join(settings!!.features, ",")
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
        } catch (e: ExecutionException) {
            throw ExternalSystemException(e)
        }

        if (processOut.exitCode != 0) {
            //
            // NOTE:
            //  Since `metadata` isn't made its way into Cargo bundle (yet),
            //  this particular check verifies whether user has it installed already or not.
            //  Hopefully based on the following lines
            //
            //  https://github.com/rust-lang/cargo/blob/master/src/bin/cargo.rs#L189 (`execute_subcommand`)
            //
            throw ExternalSystemException("Failed to execute cargo: ${processOut.stderr}")
        }

        // drop leading "Downloading foo ..." stuff
        val json = processOut.stdout.dropWhile { it != '{' }

        return CargoMetadata.fromJson(json)
    }

    private fun createModuleNode(module: CargoMetadata.Module, projectNode: DataNode<ProjectData>): DataNode<ModuleData> {
        val root = module.contentRoot.absolutePath
        val modData =
            ModuleData(
                module.name,
                CargoProjectSystem.ID,
                module.moduleType,
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

        moduleNode.createChild(ProjectKeys.CONTENT_ROOT, content)
        return moduleNode
    }

    private fun createLibraryNode(lib: CargoMetadata.Library, projectNode: DataNode<ProjectData>): DataNode<LibraryData> {
        val libData = LibraryData(CargoProjectSystem.ID, "${lib.name} ${lib.version}")
        libData.addPath(LibraryPathType.SOURCE, lib.contentRoot.absolutePath)
        val libNode = projectNode.createChild(ProjectKeys.LIBRARY, libData)
        return libNode
    }
}
