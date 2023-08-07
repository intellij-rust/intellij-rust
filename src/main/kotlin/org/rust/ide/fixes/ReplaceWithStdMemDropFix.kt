/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.unwrapReference

class ReplaceWithStdMemDropFix(call: PsiElement) : RsQuickFixBase<PsiElement>(call) {
    override fun getFamilyName() = text
    override fun getText() = RsBundle.message("intention.name.replace.with.std.mem.drop")

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val (old, args) = when (element) {
            is RsCallExpr -> {
                val args = element.valueArgumentList.exprList

                val dropArgs = if (args.size == 1) {
                    // Change `Foo::drop(&mut x)` to `std::mem::drop(x)`
                    // Convert correct code to correct code without the user manually removing `&mut `
                    val self = args[0].unwrapReference()
                    listOf(self)
                } else {
                    // Change `Foo::drop(a, &b, &mut c)` to `std::mem::drop(a, &b, &mut c)`
                    // (handles incorrect code gracefully)
                    args
                }
                element to dropArgs
            }
            is RsMethodCall -> {
                // RsMethodCall   -> drop()
                // RsDotExpr      -> foo.drop()
                // RsDotExpr.expr -> foo
                val dotExpr = element.parent as? RsDotExpr ?: return
                val expr = dotExpr.expr
                // args inside the drop method. Should be empty but check them just in case (drop(x, y, z) -> x, y, z)
                val args = element.valueArgumentList.exprList

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

    private fun createStdMemDropCall(project: Project, args: Iterable<RsExpr>) =
        RsPsiFactory(project).createFunctionCall("std::mem::drop", args)
}
