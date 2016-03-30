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
import org.rust.cargo.util.getService

/**
 * Populates [CargoModuleService] on project import
 */
class CargoModuleDataService : AbstractProjectDataService<CargoModuleData, Module>() {
    override fun getTargetDataKey(): Key<CargoModuleData> = CargoConstants.KEYS.CARGO_MODULE_DATA

    override fun postProcess(toImport: Collection<DataNode<CargoModuleData>>,
                             projectData: ProjectData?,
                             project: Project,
                             modelsProvider: IdeModifiableModelsProvider) {
        for (node in toImport) {
            val moduleName = node.getData(ProjectKeys.MODULE)?.internalName ?: continue
            val module = modelsProvider.modules.find { it.name == moduleName } ?: continue
            val service = module.getService<CargoModuleService>()
            service.saveData(node.data.targets, node.data.externCrates)
        }
    }
}
