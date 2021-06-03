/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.RsInnerAttr
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.isRootMetaItem
import org.rust.lang.core.psi.ext.stringValue

class RsUnknownCrateTypesInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.UnknownCrateTypes

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsVisitor() {
            override fun visitLitExpr(o: RsLitExpr) {
                val parent = o.parent as? RsMetaItem ?: return
                if (parent.path?.text != "crate_type") return

                val context = ProcessingContext()
                if (!parent.isRootMetaItem(context)) return
                if (context.get(RsPsiPattern.META_ITEM_ATTR) !is RsInnerAttr) return

                if (o.stringValue !in KNOWN_CRATE_TYPES) {
                    holder.registerLintProblem(o, "Invalid `crate_type` value")
                }
            }
        }

    companion object {
        private val KNOWN_CRATE_TYPES = listOf("bin", "lib", "dylib", "staticlib", "cdylib", "rlib", "proc-macro")
    }
}
