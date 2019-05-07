/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.UnaryOperator
import org.rust.lang.core.psi.ext.operatorType

class ReplaceWithStdMemDropFix(call: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(call) {
    override fun getFamilyName() = text
    override fun getText() = "Replace with `std::mem::drop`"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val (old, args) = when (startElement) {
            is RsCallExpr -> {
                val args = startElement.valueArgumentList.exprList

                val dropArgs = if (args.size == 1) {
                    // Change `Foo::drop(&mut x)` to `std::mem::drop(x)`
                    // Convert correct code to correct code without the user manually removing `&mut `
                    val self = args[0].unwrapRef()
                    listOf(self)
                } else {
                    // Change `Foo::drop(a, &b, &mut c)` to `std::mem::drop(a, &b, &mut c)`
                    // (handles incorrect code gracefully)
                    args
                }
                startElement to dropArgs
            }
            is RsMethodCall -> {
                // RsMethodCall   -> drop()
                // RsDotExpr      -> foo.drop()
                // RsDotExpr.expr -> foo
                val dotExpr = startElement.parent as? RsDotExpr ?: return
                val expr = dotExpr.expr
                // args inside the drop method. Should be empty but check them just in case (drop(x, y, z) -> x, y, z)
                val args = startElement.valueArgumentList.exprList

                val dropArgs = if (args.isEmpty()) {
                    // Change `foo.drop()` to `std::mem::drop(foo)`
                    listOf(expr)
                } else {
                    // Change `foo.drop(a, &b, &mut c)` to `std::mem::drop(foo, a, &b, &mut c)`
                    // (handles incorrect code gracefully)
                    listOf(expr) + args
                }
                dotExpr to dropArgs
            }
            else -> return
        }

        old.replace(createStdMemDropCall(project, args))
    }

    private fun RsExpr.unwrapRef(): RsExpr {
        if (this !is RsUnaryExpr) return this
        if (operatorType == UnaryOperator.REF || operatorType == UnaryOperator.REF_MUT)
            return expr ?: this
        return this
    }

    private fun createStdMemDropCall(project: Project, args: Iterable<RsExpr>) =
        RsPsiFactory(project).createFunctionCall("std::mem::drop", args)
}
