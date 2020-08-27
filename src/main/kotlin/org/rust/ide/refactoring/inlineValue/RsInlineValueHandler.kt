/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inlineValue

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.annotations.TestOnly
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsNameIdentifierOwner
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.resolve.ref.RsReference

class RsInlineValueHandler : InlineActionHandler() {
    override fun isEnabledForLanguage(language: Language): Boolean = language is RsLanguage
    override fun inlineElement(project: Project, editor: Editor, element: PsiElement) {
        var reference = TargetElementUtil.findReference(editor, editor.caretModel.offset) as? RsReference

        // if invoked directly on the target value, ignore the reference
        if (reference?.element == element) {
            reference = null
        }
        val context = getContext(project, editor, element, reference) ?: return

        val dialog = RsInlineValueDialog(context)
        if (!isUnitTestMode && dialog.shouldBeShown()) {
            dialog.show()
            if (!dialog.isOK) {
                val statusBar = WindowManager.getInstance().getStatusBar(project)
                statusBar?.info = RefactoringBundle.message("press.escape.to.remove.the.highlighting")
            }
        } else {
            val processor = getProcessor(project, context)
            processor.setPreviewUsages(false)
            processor.run()
        }
    }

    override fun canInlineElement(element: PsiElement): Boolean {
        return (element is RsConstant && element.navigationElement is RsConstant) ||
            (element is RsPatBinding && element.navigationElement is RsPatBinding)
    }
}

private var MOCK: InlineValueMode? = null

@TestOnly
fun withMockInlineValueMode(mock: InlineValueMode, action: () -> Unit) {
    MOCK = mock
    try {
        action()
    } finally {
        MOCK = null
    }
}

private fun getProcessor(project: Project, context: InlineValueContext): RsInlineValueProcessor {
    val mode = if (isUnitTestMode && MOCK != null) {
        MOCK!!
    } else {
        InlineValueMode.InlineAllAndRemoveOriginal
    }

    return RsInlineValueProcessor(
        project,
        context,
        mode
    )
}

private fun getContext(
    project: Project,
    editor: Editor,
    element: PsiElement,
    reference: RsReference?
): InlineValueContext? =
    getVariableDeclContext(project, editor, element, reference)
        ?: getConstantContext(project, editor, element, reference)

private fun getConstantContext(
    project: Project,
    editor: Editor,
    element: PsiElement,
    reference: RsReference?
): InlineValueContext? {
    val const = element as? RsConstant ?: return null
    val expr = const.expr
    if (expr == null) {
        showErrorHint(project, editor, "cannot inline constant without an expression")
        return null
    }

    return InlineValueContext.Constant(const, expr, reference)
}

private fun getVariableDeclContext(
    project: Project,
    editor: Editor,
    element: PsiElement,
    reference: RsReference?
): InlineValueContext? {
    val binding = element as? RsPatBinding ?: return null
    val decl = binding.ancestorOrSelf<RsLetDecl>()
    val expr = decl?.expr
    if (expr == null) {
        showErrorHint(project, editor, "cannot inline variable without an expression")
        return null
    }
    if (decl.pat !is RsPatIdent) {
        showErrorHint(project, editor, "cannot inline variable without an identifier")
        return null
    }
    return InlineValueContext.Variable(binding, decl, expr, reference)
}

sealed class InlineValueContext(val element: RsNameIdentifierOwner, val expr: RsExpr, val reference: RsReference?) {
    abstract fun delete()

    class Constant(constant: RsConstant, expr: RsExpr, reference: RsReference? = null)
        : InlineValueContext(constant, expr, reference) {
        override fun delete() {
            element.delete()
        }
    }

    class Variable(variable: RsPatBinding, private val decl: RsLetDecl, expr: RsExpr, reference: RsReference? = null)
        : InlineValueContext(variable, expr, reference) {
        override fun delete() {
            decl.delete()
        }
    }
}

sealed class InlineValueMode {
    object InlineThisOnly : InlineValueMode()
    object InlineAllAndKeepOriginal : InlineValueMode()
    object InlineAllAndRemoveOriginal : InlineValueMode()
}

private fun showErrorHint(project: Project, editor: Editor, message: String) {
    CommonRefactoringUtil.showErrorHint(
        project,
        editor,
        message,
        RefactoringBundle.message("inline.variable.title"),
        "refactoring.inline"
    )
}
