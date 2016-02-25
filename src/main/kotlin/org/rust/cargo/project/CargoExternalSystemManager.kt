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

class CargoExternalSystemManager : ExternalSystemAutoImportAware by CachingExternalSystemAutoImportAware(CargoAutoImport)
                                 , ExternalSystemManager<CargoProjectSettings,
                                                         CargoProjectSettingsListener,
                                                         CargoSettings,
                                                         CargoLocalSettings,
                                                         CargoExecutionSettings> {


    override fun getSystemId(): ProjectSystemId = CargoProjectSystem.ID

    override fun getSettingsProvider(): Function<Project, CargoSettings> =
        Function { CargoSettings.getInstance(it) }

    override fun getLocalSettingsProvider(): Function<Project, CargoLocalSettings> =
        Function { CargoLocalSettings.getInstance(it) }

    override fun getExecutionSettingsProvider(): Function<Pair<Project, String>, CargoExecutionSettings> =
        Function { pair ->
            val project = pair.first
            val linkedProjectPath = pair.second
            val settings = CargoSettings.getInstance(project)
            val projectSettings = settings.getLinkedProjectSettings(linkedProjectPath)
            CargoExecutionSettings.from(projectSettings)
        }

    override fun getProjectResolverClass(): Class<out ExternalSystemProjectResolver<CargoExecutionSettings>> =
        CargoProjectResolver::class.java

    override fun getTaskManagerClass(): Class<out ExternalSystemTaskManager<CargoExecutionSettings>> =
        CargoTaskManager::class.java

    override fun getExternalProjectDescriptor(): FileChooserDescriptor =
        CargoOpenProjectDescriptor

    @Throws(ExecutionException::class)
    override fun enhanceRemoteProcessing(parameters: SimpleJavaParameters) {
        val classesToAdd = listOf(
            StringUtils::class.java,
            Gson::class.java,
            TypeCastException::class.java,
            Charsets::class.java
        )
        for (clazz in classesToAdd) {
            parameters.classPath.add(PathUtil.getJarPathForClass(clazz))
        }
        //parameters.getVMParametersList().add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
    }

    override fun enhanceLocalProcessing(urls: List<URL>) {
    }

}
