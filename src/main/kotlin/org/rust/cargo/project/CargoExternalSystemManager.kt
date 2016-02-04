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
import java.net.URL

class CargoExternalSystemManager : ExternalSystemAutoImportAware,
        ExternalSystemManager<CargoProjectSettings, CargoProjectSettingsListener, CargoSettings,
                CargoLocalSettings, CargoExecutionSettings> {

    override fun getSystemId(): ProjectSystemId {
        return CargoProjectSystem.ID
    }

    override fun getSettingsProvider(): Function<Project, CargoSettings> =
        Function { p -> CargoSettings.getInstance(p) }

    override fun getLocalSettingsProvider(): Function<Project, CargoLocalSettings> =
        Function { p -> CargoLocalSettings.getInstance(p) }

    override fun getExecutionSettingsProvider(): Function<Pair<Project, String>, CargoExecutionSettings> =
        Function {
            pair -> executionSettingsFor(pair.getFirst(), pair.getSecond())
        }

    override fun getProjectResolverClass(): Class<out ExternalSystemProjectResolver<CargoExecutionSettings>> =
        CargoProjectResolver::class.java

    override fun getTaskManagerClass(): Class<out ExternalSystemTaskManager<CargoExecutionSettings>> =
        CargoTaskManager::class.java

    override fun getExternalProjectDescriptor(): FileChooserDescriptor =
        CargoOpenProjectDescriptor()

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
            return CargoExecutionSettings.from(ServiceManager.getService(project, CargoProjectSettings::class.java))
        }
    }
}
