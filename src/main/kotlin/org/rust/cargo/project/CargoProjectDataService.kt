package org.rust.cargo.project

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import org.rust.cargo.CargoConstants

class CargoProjectDataService : AbstractProjectDataService<CargoProjectData, Project>() {
    override fun getTargetDataKey(): Key<CargoProjectData> = CargoConstants.KEYS.CARGO_PROJECT_DATA

    override fun postProcess(toImport: Collection<DataNode<CargoProjectData>>,
                             projectData: ProjectData?,
                             project: Project,
                             modelsProvider: IdeModifiableModelsProvider) {
        if (toImport.isEmpty()) {
            return
        }

        val cargoProjectData = toImport.single().data
        val sdkName = cargoProjectData.sdkName ?: return
        if (ProjectRootManager.getInstance(project).projectSdk == null) {
            updateSdk(project, sdkName)
        }

    }

    private fun updateSdk(project: Project, sdkName: String) {
        val sdk = findSdk(sdkName) ?: return

        ExternalSystemApiUtil.executeProjectChangeAction(object : DisposeAwareProjectChange(project) {
            override fun execute() {
                ProjectRootManager.getInstance(project).projectSdk = sdk
            }
        })
    }

    private fun findSdk(sdkName: String): Sdk? =
        ProjectJdkTable.getInstance().findJdk(sdkName)
}

