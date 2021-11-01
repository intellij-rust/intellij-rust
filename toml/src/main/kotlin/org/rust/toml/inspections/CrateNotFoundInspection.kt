/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.stdext.unwrapOrElse
import org.rust.toml.crates.local.CratesLocalIndexService
import org.toml.lang.psi.TomlVisitor

class CrateNotFoundInspection : CargoTomlInspectionToolBase() {
    override val requiresLocalCrateIndex: Boolean = true

    override fun buildCargoTomlVisitor(holder: ProblemsHolder): TomlVisitor {
        return object : CargoDependencyCrateVisitor() {
            override fun visitDependency(dependency: DependencyCrate) {
                if (dependency.isForeign()) return

                val crateName = dependency.crateName
                val crate = CratesLocalIndexService.getInstance().getCrate(crateName).unwrapOrElse { return }

                if (crate == null) {
                    holder.registerProblem(dependency.crateNameElement, "Crate $crateName not found")
                }
            }
        }
    }
}
