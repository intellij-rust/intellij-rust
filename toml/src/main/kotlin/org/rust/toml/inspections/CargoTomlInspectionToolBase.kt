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
import org.toml.lang.psi.TomlVisitor

abstract class CargoTomlInspectionToolBase : TomlLocalInspectionToolBase() {
    open val requiresLocalCrateIndex: Boolean = false

    abstract fun buildCargoTomlVisitor(holder: ProblemsHolder): TomlVisitor

    override fun buildVisitorInternal(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor? {
        return when {
            requiresLocalCrateIndex && !isFeatureEnabled(RsExperiments.CRATES_LOCAL_INDEX) -> null
            holder.file.name != CargoConstants.MANIFEST_FILE -> null
            else -> buildCargoTomlVisitor(holder)
        }
    }
}
