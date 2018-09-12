/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.inspections.fixes.AddCrateKeywordFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.basePath
import org.rust.lang.core.psi.ext.containingCargoTarget
import org.rust.lang.core.psi.ext.qualifier

class RsAnchoredPathsInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : RsVisitor() {
        override fun visitPath(path: RsPath) {
            val parent = path.parent
            val edition = path.containingCargoTarget?.edition

            if (edition == CargoWorkspace.Edition.EDITION_2018 && parent is RsUseSpeck && parent.qualifier == null) {
                checkPathInUseItem(path, holder)
            }
        }
    }

    private fun checkPathInUseItem(path: RsPath, holder: ProblemsHolder) {
        val basePath = path.basePath()
        basePath.node.findChildByType(tokenSetOf(RsElementTypes.IDENTIFIER, RsElementTypes.CSELF)) ?: return

        val element = basePath.reference.resolve()
        if (element is RsMod && element.isCrateRoot) return

        val fixes = if (element != null && element.crateRoot == path.crateRoot && element.containingMod.isCrateRoot) {
            arrayOf(AddCrateKeywordFix(path))
        } else {
            emptyArray()
        }

        holder.registerProblem(
            path,
            "Paths in `use` declarations should start with a crate name, or with `crate`, `super`, or `self`",
            *fixes
        )
    }
}
