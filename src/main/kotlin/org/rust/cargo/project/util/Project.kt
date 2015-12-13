package org.rust.cargo.project.util

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import org.rust.cargo.project.CargoProjectSystem
import java.nio.file.Path
import java.nio.file.Paths

object Util {

    val projectDataManager by lazy { ProjectDataManager.getInstance() }

    fun getProjectDataNode(p: Project): DataNode<ProjectData>? =
        projectDataManager   .getExternalProjectData(p, CargoProjectSystem.ID, p.basePath!!)
                            ?.externalProjectStructure

    fun getProjectModulesDataNodes(p: Project): Collection<DataNode<ModuleData>> =
        getProjectDataNode(p)?.let {
            it.children
                .map {
                    c -> c.getDataNode(ProjectKeys.MODULE)
                }
                .filterNotNull()
        } ?: ContainerUtil.emptyList()

}

fun Project.getProjectData(): ProjectData? =
    Util.getProjectDataNode(this)?.getData(ProjectKeys.PROJECT)

fun Project.getModulesData(): Collection<ModuleData> =
    Util.getProjectModulesDataNodes(this)
        .map { it.data }

fun Project.getSourceRootFor(path: Path): Path? =
    Util.getProjectModulesDataNodes(this)
        .flatMap { it.children }
        .flatMap {
            it.getData(ProjectKeys.CONTENT_ROOT)?.let {
                it.getPaths(ExternalSystemSourceType.SOURCE).map { Paths.get(it.path) }
            } ?: ContainerUtil.emptyList()
        }
        .find { path.startsWith(it) }

