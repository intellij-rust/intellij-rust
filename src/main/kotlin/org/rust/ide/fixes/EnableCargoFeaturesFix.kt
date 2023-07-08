/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageFeature

class EnableCargoFeaturesFix(
    private val cargoProject: CargoProject,
    private val features: Set<PackageFeature>
) : LocalQuickFix {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.enable.features")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        project.cargoProjects.modifyFeatures(cargoProject, features, FeatureState.Enabled)
    }
}
