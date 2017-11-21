/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template

import com.intellij.codeInsight.template.EverywhereContextType
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.rust.ide.highlight.RsHighlighter
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.ancestorStrict
import kotlin.reflect.KClass

sealed class RsContextType(
    id: String,
    presentableName: String,
    baseContextType: KClass<out TemplateContextType>
) : TemplateContextType(id, presentableName, baseContextType.java) {

    final override fun isInContext(file: PsiFile, offset: Int): Boolean {
        if (!PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(RsLanguage)) {
            return false
        }

        val element = file.findElementAt(offset)
        if (element == null || element is PsiComment || element.parent is RsLitExpr) {
            return false
        }

        return isInContext(element)
    }

    abstract protected fun isInContext(element: PsiElement): Boolean

    override fun createHighlighter(): SyntaxHighlighter = RsHighlighter()

    class Generic : RsContextType("RUST_FILE", "Rust", EverywhereContextType::class) {
        override fun isInContext(element: PsiElement): Boolean = true
    }

    class Statement : RsContextType("RUST_STATEMENT", "Statement", Generic::class) {
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

            return true
        }
    }

    class Item : RsContextType("RUST_ITEM", "Item", Generic::class) {
        override fun isInContext(element: PsiElement): Boolean =
            // We are inside item but there is no block between
            owner(element) is RsItemElement
    }

    class Struct : RsContextType("RUST_STRUCT", "Structure", Item::class) {
        override fun isInContext(element: PsiElement): Boolean =
            // Structs can't be nested or contain other expressions,
            // so it is ok to look for any Struct ancestor.
            element.ancestorStrict<RsStructItem>() != null
    }

    class Mod : RsContextType("RUST_MOD", "Module", Item::class) {
        override fun isInContext(element: PsiElement): Boolean
            // We are inside RsMod
            = owner(element) is RsMod
    }

    class Attribute : RsContextType("RUST_ATTRIBUTE", "Attribute", Item::class) {
        override fun isInContext(element: PsiElement): Boolean =
            element.ancestorStrict<RsAttr>() != null
    }

    companion object {
        private fun owner(element: PsiElement): PsiElement? = PsiTreeUtil.findFirstParent(element) {
            it is RsBlock || it is RsItemElement || it is RsAttr || it is PsiFile
        }
    }
}
