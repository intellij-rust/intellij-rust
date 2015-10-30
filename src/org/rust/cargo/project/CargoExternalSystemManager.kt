package org.rust.cargo.project

import com.google.gson.Gson
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.externalSystem.service.project.autoimport.CachingExternalSystemAutoImportAware
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.util.Function
import com.intellij.util.PathUtil
import org.apache.commons.lang.StringUtils
import org.rust.cargo.project.settings.*
import org.rust.lang.config.RustConfigService

import java.net.URL

class CargoExternalSystemManager : ExternalSystemAutoImportAware,
        ExternalSystemManager<CargoProjectSettings, CargoProjectSettingsListener, CargoSettings,
                CargoLocalSettings, CargoExecutionSettings> {

    override fun getSystemId(): ProjectSystemId {
        return CargoProjectSystem.ID
    }

    override fun getSettingsProvider(): Function<Project, CargoSettings> {
        return object : Function<Project, CargoSettings> {
            override fun `fun`(project: Project): CargoSettings {
                return CargoSettings.getInstance(project)
            }
        }
    }

    override fun getLocalSettingsProvider(): Function<Project, CargoLocalSettings> {
        return object : Function<Project, CargoLocalSettings> {
            override fun `fun`(project: Project): CargoLocalSettings {
                return CargoLocalSettings.getInstance(project)
            }
        }
    }

    override fun getExecutionSettingsProvider(): Function<Pair<Project, String>, CargoExecutionSettings> {
        return object : Function<Pair<Project, String>, CargoExecutionSettings> {
            override fun `fun`(pair: Pair<Project, String>): CargoExecutionSettings {
                return executionSettingsFor(pair.getFirst(), pair.getSecond())
            }
        }
    }

    override fun getProjectResolverClass(): Class<out ExternalSystemProjectResolver<CargoExecutionSettings>> {
        return CargoProjectResolver::class.java
    }

    override fun getTaskManagerClass(): Class<out ExternalSystemTaskManager<CargoExecutionSettings>> {
        return CargoTaskManager::class.java
    }

    override fun getExternalProjectDescriptor(): FileChooserDescriptor {
        return CargoOpenProjectDescriptor()
    }

    private val delegate = CachingExternalSystemAutoImportAware(CargoAutoImport())

    override fun getAffectedExternalProjectPath(changedFileOrDirPath: String, project: Project): String? {
        return delegate.getAffectedExternalProjectPath(changedFileOrDirPath, project)
    }

    @Throws(ExecutionException::class)
    override fun enhanceRemoteProcessing(parameters: SimpleJavaParameters) {
        parameters.classPath.add(PathUtil.getJarPathForClass(StringUtils::class.java))
        parameters.classPath.add(PathUtil.getJarPathForClass(Gson::class.java))
        parameters.classPath.add(PathUtil.getJarPathForClass(TypeCastException::class.java))
        parameters.classPath.add(PathUtil.getJarPathForClass(kotlin.Charsets::class.java))
        //parameters.getVMParametersList().add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
    }

    override fun enhanceLocalProcessing(urls: List<URL>) {
    }

    companion object {
        internal fun executionSettingsFor(project: Project, path: String): CargoExecutionSettings {
            return CargoExecutionSettings(
                    ServiceManager.getService(RustConfigService::class.java).state.cargoBinary,
                    ServiceManager.getService(project, CargoProjectSettings::class.java).features)
        }
    }
}
