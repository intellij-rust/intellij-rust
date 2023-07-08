/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.rust.RsBundle
import org.rust.ide.highlight.RsHighlighter
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.doc.psi.RsDocComment

sealed class RsContextType(@NlsContexts.Label presentableName: String) : TemplateContextType(presentableName) {

    final override fun isInContext(context: TemplateActionContext): Boolean {
        if (!PsiUtilCore.getLanguageAtOffset(context.file, context.startOffset).isKindOf(RsLanguage)) {
            return false
        }

        val element = context.file.findElementAt(context.startOffset)
        if (element == null || element is PsiComment || element.parent is RsLitExpr) {
            return false
        }

        return isInContext(element)
    }

    protected abstract fun isInContext(element: PsiElement): Boolean

    override fun createHighlighter(): SyntaxHighlighter = RsHighlighter()

    class Generic : RsContextType(RsBundle.message("label.rust")) {
        override fun isInContext(element: PsiElement): Boolean = true
    }

    class Statement : RsContextType(RsBundle.message("label.statement")) {
        override fun isInContext(element: PsiElement): Boolean {
            // Used to support cases when identifier is parsed together with next statement, e.g.:
            // fn main() {
            //     p/*caret*/
            //     ::foo();
            // }
            val stmt = element.ancestorStrict<RsExprStmt>() ?: return false
            return element.startOffset == stmt.startOffset
        }
    }

    class Expression : RsContextType(RsBundle.message("label.expression")) {
        override fun isInContext(element: PsiElement): Boolean {
            // We are inside block but there is no item nor attr between
            if (owner(element) !is RsBlock) return false
            val parent = element.parent

            // foo::element
            if (parent is RsPath && parent.coloncolon != null) return false

            // foo.element
            if (parent is RsFieldLookup) return false

            // foo.element()
            if (parent is RsMethodCall) return false

            // 'label
            if (parent is RsLabel) return false

            return true
        }
    }

    class Item : RsContextType(RsBundle.message("label.item")) {
        override fun isInContext(element: PsiElement): Boolean =
            // We are inside item but there is no block between
            owner(element) is RsItemElement
    }

    class Struct : RsContextType(RsBundle.message("label.structure")) {
        override fun isInContext(element: PsiElement): Boolean =
            // Structs can't be nested or contain other expressions,
            // so it is ok to look for any Struct ancestor.
            element.ancestorStrict<RsStructItem>() != null
    }

    class Mod : RsContextType(RsBundle.message("label.module")) {
        override fun isInContext(element: PsiElement): Boolean
            // We are inside RsMod
            = owner(element) is RsMod
    }

    class Attribute : RsContextType(RsBundle.message("label.attribute")) {
        override fun isInContext(element: PsiElement): Boolean =
            element.ancestorStrict<RsAttr>() != null
    }

    companion object {
        private fun owner(element: PsiElement): PsiElement? = PsiTreeUtil.findFirstParent(element) {
            it is RsBlock || it is RsPat || it is RsItemElement || it is PsiFile
                || it is RsAttr || it is RsDocComment || it is RsMacro || it is RsMacroCall
        }
    }
}
