/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import org.rust.ide.intentions.RsElementBaseIntentionAction
import org.rust.lang.core.psi.*
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.ext.*

class AddTurbofishFix : RsElementBaseIntentionAction<AddTurbofishFix.Context>() {
    private val TURBOFISH = "::"

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (matchExpr, insertion) = ctx
        val expression = matchExpr.text
        val left = expression.substring(0, insertion)
        val right = expression.substring(insertion)
        val fixed = create<RsExpr>(project, """$left$TURBOFISH$right""") ?: return
        matchExpr.replace(fixed)
    }

    data class Context(
        val matchExpr: RsBinaryExpr,
        val offset: Int
    )

    override fun getText() = "Add turbofish operator"
    override fun getFamilyName() = text

    private fun resolveMatchExpression(element: PsiElement): RsBinaryExpr? {
        val base = element.ancestorStrict<RsBinaryExpr>() ?: return null
        if (base.left !is RsBinaryExpr) {
            return resolveMatchExpression(base) ?: base
        }
        val left = base.left as RsBinaryExpr
        if (left.operatorType == ArithmeticOp.SHR) {
            return resolveMatchExpression(base) ?: base
        }
        return base
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val m = resolveMatchExpression(element) ?: return null
        return guessContext(project, m)
    }

    private fun innerOffset(root: PsiElement, child: PsiElement): Int? {
        if (child == root) {
            return 0
        }
        val parent = child.parent ?: return null
        val upper = innerOffset(root, parent) ?: return null
        return upper + child.startOffsetInParent
    }

    private fun guessContext(project: Project, binary: RsBinaryExpr): Context? {
        val nodes = bfsLeafs(binary)
        val called = rightBoundary(nodes) ?: return null
        val typeListEndIndex = binary.textLength - called.textLength

        val turbofish_offset = nodes.takeWhile { it != called }.map {
            val offset = innerOffset(binary, it)!! + it.textLength
            val typeListCandidate = binary.text.substring(offset, typeListEndIndex)
            if (isTypeArgumentList(project, typeListCandidate)) {
                offset
            } else {
                null
            }
        }.filterNotNull().firstOrNull() ?: return null
        return Context(binary, turbofish_offset)
    }

    private fun rightBoundary(nodes: List<RsExpr>) = nodes.asReversed().firstOrNull { isCallExpression(it) }

    private fun isTypeArgumentList(project: Project, candidate: String) =
        create<RsCallExpr>(project, "something$TURBOFISH$candidate()") != null

    private fun bfsLeafs(expr: RsExpr?): List<RsExpr> {
        return when (expr) {
            is RsBinaryExpr -> {
                bfsLeafs(expr.left) + bfsLeafs(expr.right)
            }
            null -> listOf<RsExpr>()
            else -> listOf(expr)
        }
    }

    private fun isCallExpression(expr: RsExpr) = expr is RsParenExpr || expr.firstChild is RsParenExpr

    private inline fun <reified T : RsElement> create(project: Project, text: String): T? =
        createFromText(project, "fn main() { $text; }")

    private inline fun <reified T : RsElement> createFromText(project: Project, code: String): T? =
        PsiFileFactory.getInstance(project)
            .createFileFromText("DUMMY.rs", RsFileType, code)
            .descendantOfTypeStrict<T>()
}
