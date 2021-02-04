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

abstract class CargoTomlInspectionToolBase : TomlLocalInspectionToolBase() {
    open val requiresLocalCrateIndex: Boolean = false

    abstract fun buildCargoTomlVisitor(holder: ProblemsHolder): PsiElementVisitor?

    override fun buildVisitorInternal(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor? {
        if (requiresLocalCrateIndex && !isFeatureEnabled(RsExperiments.CRATES_LOCAL_INDEX)) {
            return super.buildVisitor(holder, isOnTheFly)
        }
        return if (holder.file.name != CargoConstants.MANIFEST_FILE) {
            super.buildVisitor(holder, isOnTheFly)
        } else {
            buildCargoTomlVisitor(holder)
        }
    }
}
