/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.psi.PsiFile
import org.rust.cargo.CargoConstants
import org.rust.ide.inspections.RsMissingFeaturesInspection
import org.rust.lang.core.psi.ext.findCargoPackage
import org.rust.lang.core.psi.ext.findCargoProject
import org.rust.openapiext.pathAsPath

class CargoTomlMissingFeaturesInspection : LocalInspectionTool() {
    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor?>? {
        if (file.name != CargoConstants.MANIFEST_FILE) return null

        val cargoProject = file.findCargoProject() ?: return null
        val pkg = file.findCargoPackage()?.takeIf { it.rootDirectory == file.virtualFile?.parent?.pathAsPath } ?: return null
        val missingFeatures = RsMissingFeaturesInspection.collectMissingFeaturesForPackage(pkg)

        return RsMissingFeaturesInspection.createProblemDescriptors(missingFeatures, manager, file, isOnTheFly, cargoProject)
    }
}
