/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsValueParameter
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.RsFunctionOwner
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.psi.ext.parentOfType

class RsAnonymousParameterInspection : RsLocalInspectionTool() {
    override fun getDisplayName(): String = "Anonymous function parameter"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : RsVisitor() {
        override fun visitValueParameter(o: RsValueParameter) {
            if (o.pat != null) return
            val fn = o.parentOfType<RsFunction>() ?: return
            if (o.parentOfType<RsPath>() != null) return
            if (fn.owner is RsFunctionOwner.Trait) {
                holder.registerProblem(o,
                    "Anonymous functions parameters are deprecated (RFC 1685)",
                    SubstituteTextFix(fn.containingFile, o.textRange, "_: ${o.text}", "Add dummy parameter name")
                )
            }
        }
    }
}
