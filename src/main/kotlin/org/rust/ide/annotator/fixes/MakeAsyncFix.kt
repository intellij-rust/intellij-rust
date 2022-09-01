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
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsLambdaExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsFunctionOrLambda
import org.rust.lang.core.psi.ext.isAsync

class MakeAsyncFix(function: RsFunctionOrLambda) : LocalQuickFixAndIntentionActionOnPsiElement(function) {

    private val isFunction: Boolean = function is RsFunction

    override fun getText(): String {
        val item = if (isFunction) "function" else "lambda"
        return "Make $item async"
    }

    override fun getFamilyName(): String = "Make async"

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val function = startElement as? RsFunctionOrLambda ?: return
        if (function.isAsync) return
        val anchor = when (function) {
            is RsFunction -> function.unsafe ?: function.externAbi ?: function.fn
            is RsLambdaExpr -> function.move ?: function.valueParameterList
            else -> error("unreachable")
        }
        function.addBefore(RsPsiFactory(project).createAsyncKeyword(), anchor)
    }
}
