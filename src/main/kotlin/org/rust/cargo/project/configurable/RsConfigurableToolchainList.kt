/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import org.rust.ide.sdk.RsSdkComparator
import org.rust.ide.sdk.RsSdkType

class RsConfigurableToolchainList {
    private var _model: ProjectSdksModel? = null
    val model: ProjectSdksModel
        get() {
            if (_model == null) {
                _model = ProjectSdksModel().apply { reset(null) }
            }
            return checkNotNull(_model) { "Set to null by another thread" }
        }

    val allRustSdks: List<Sdk>
        get() = model.sdks.filter { it.sdkType is RsSdkType }.sortedWith(RsSdkComparator)

    fun disposeModel() {
        _model?.disposeUIResources()
        _model = null
    }

    companion object {
        fun getInstance(project: Project?): RsConfigurableToolchainList {
            val effectiveProject = project ?: ProjectManager.getInstance().defaultProject
            val instance = effectiveProject.service<RsConfigurableToolchainList>()
            if (effectiveProject != project) {
                instance.disposeModel()
            }
            return instance
        }
    }
}
