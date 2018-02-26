/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.rust.ide.inspections.fixes.import.AutoImportFix
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsVisitor

class RsUnresolvedReferenceInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Unresolved reference"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitPath(o: RsPath) {
                val (basePath, _) = AutoImportFix.findApplicableContext(holder.project, o) ?: return
                // TODO: add inspection option to register all problems
                holder.registerProblem(o, "Unresolved reference: `${basePath.text}`",
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, AutoImportFix())
            }
        }
}
