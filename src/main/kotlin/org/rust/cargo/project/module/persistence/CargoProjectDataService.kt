package org.rust.cargo.project.module.persistence

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.rust.cargo.CargoConstants
import org.rust.cargo.CargoProjectDescription
import org.rust.cargo.util.getService

/**
 * Populates [CargoModuleTargetsService] on project import
 */
class CargoProjectDataService : AbstractProjectDataService<CargoProjectDescription.Target, Module>() {
    override fun getTargetDataKey(): Key<CargoProjectDescription.Target> = CargoConstants.KEYS.TARGET

    override fun postProcess(toImport: Collection<DataNode<CargoProjectDescription.Target>>,
                             projectData: ProjectData?,
                             project: Project,
                             modelsProvider: IdeModifiableModelsProvider) {
        val targetsByModule = toImport.groupBy { it.getData(ProjectKeys.MODULE)?.internalName }

        for ((moduleName, targets) in targetsByModule) {
            val module = modelsProvider.modules.find { it.name == moduleName } ?: continue
            val service = module.getService<CargoModuleTargetsService>()
            service.saveTargets(targets.map { it.data })
        }
    }
}
