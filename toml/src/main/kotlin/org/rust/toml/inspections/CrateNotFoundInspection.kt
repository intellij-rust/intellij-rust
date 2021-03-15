/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.cargo.CargoConstants
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled
import org.rust.toml.crates.local.CratesLocalIndexException
import org.rust.toml.crates.local.CratesLocalIndexService

class CrateNotFoundInspection : CargoTomlInspectionToolBase() {
    override val requiresLocalCrateIndex = true

    override fun buildCargoTomlVisitor(holder: ProblemsHolder): PsiElementVisitor {
        return object : CargoDependencyCrateVisitor() {
            override fun visitDependency(dependency: DependencyCrate) {
                if (dependency.isForeign()) return

                val crate = try {
                    CratesLocalIndexService.getInstance().getCrate(dependency.crateName)
                } catch (e: CratesLocalIndexException) {
                    return
                }

                if (crate == null) {
                    holder.registerProblem(dependency.crateNameElement, "Crate ${dependency.crateName} not found")
                }
            }
        }
    }
}
