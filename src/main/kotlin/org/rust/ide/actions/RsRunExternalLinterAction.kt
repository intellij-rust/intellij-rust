/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.actions.RunInspectionIntention
import com.intellij.codeInspection.ex.InspectionManagerEx
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.runconfig.command.RunCargoCommandActionBase
import org.rust.cargo.runconfig.getAppropriateCargoProject
import org.rust.ide.inspections.RsExternalLinterInspection

class RsRunExternalLinterAction : RunCargoCommandActionBase(CargoIcons.EXTERNAL_LINTER) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val currentProfile = InspectionProjectProfileManager.getInstance(project).currentProfile
        val wrapper = currentProfile.getInspectionTool(RsExternalLinterInspection.SHORT_NAME, project) ?: return
        val managerEx = InspectionManager.getInstance(project) as InspectionManagerEx
        val inspectionContext = RunInspectionIntention.createContext(wrapper, managerEx, null)

        val cargoProject = getAppropriateCargoProject(e.dataContext)
        inspectionContext.putUserData(CARGO_PROJECT, cargoProject)

        inspectionContext.doInspections(AnalysisScope(project))
    }

    companion object {
        @JvmField
        val CARGO_PROJECT: Key<CargoProject> = Key.create("Cargo project")
    }
}
