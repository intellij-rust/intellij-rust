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
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.ty.Ty
import org.rust.stdext.mapToMutableList

/**
 * The data class represents a sequence of dereferences and references. The dereferences are defined by number of
 * dereferences and references are defied by the sequence of mutabilities.
 */
data class DerefRefPath(val derefs: Int, val refs: List<Mutability>)

/**
 * The fix applies `path.derefs` dereferences to the expression and then references of the mutability given by
 * `path.refs`. Note that correctness of the generated code is not verified.
 */
class ConvertToTyWithDerefsRefsFix(
    expr: PsiElement,
    val ty: Ty,
    val path: DerefRefPath
) : LocalQuickFixAndIntentionActionOnPsiElement(expr) {
    override fun getFamilyName(): String = "Convert to type"

    override fun getText(): String = "Convert to $ty using ${formatRefs(path)}"

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        if (startElement !is RsExpr) return
        val psiFactory = RsPsiFactory(project)
        startElement.replace(psiFactory.createRefExpr(psiFactory.createDerefExpr(startElement, path.derefs), path.refs))
    }
}

private fun formatRefs(path: DerefRefPath): String {
    val refs = path.refs.mapToMutableList { if (it.isMut) "&mut " else "&" }
    refs.add("*".repeat(path.derefs))
    return refs.joinToString("").trimEnd()
}
