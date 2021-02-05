/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.cargo.CargoConstants
import org.rust.toml.isFeatureListHeader
import org.toml.lang.psi.*
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

/**
 *  * Consider `Cargo.toml`:
 * ```
 * [features]
 * foo = ["foo"]
 *       #^ Shows error that "foo" feature depends on itself
 * ```
 */
class CargoTomlCyclicFeatureInspection : TomlLocalInspectionToolBase() {
    override fun buildVisitorInternal(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor? {
        if (holder.file.name != CargoConstants.MANIFEST_FILE) return null

        return object : TomlVisitor() {
            override fun visitLiteral(element: TomlLiteral) {
                val parentArray = element.parent as? TomlArray ?: return
                val parentKeyValue = parentArray.parent as? TomlKeyValue ?: return
                val parentTable = parentKeyValue.parent as? TomlTable ?: return
                if (!parentTable.header.isFeatureListHeader) return

                val parentFeatureName = parentKeyValue.key.text ?: return
                val featureName = (element.kind as? TomlLiteralKind.String)?.value

                if (featureName == parentFeatureName) {
                    holder.registerProblem(
                        element,
                        "Cyclic feature dependency: feature `$parentFeatureName` depends on itself"
                    )
                }
            }
        }
    }
}
