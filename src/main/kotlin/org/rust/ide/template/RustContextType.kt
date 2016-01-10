package org.rust.ide.template

import com.intellij.codeInsight.template.EverywhereContextType
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.RustStructItem


sealed class RustContextType(id: String, presentableName: String, parentContext: Class<out TemplateContextType>)
    : TemplateContextType(id, presentableName, parentContext) {

    final override fun isInContext(file: PsiFile, offset: Int): Boolean {
        if (!PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(RustLanguage)) {
            return false
        }
        val element = file.findElementAt(offset)

        return element != null && isInContextImpl(element)
    }

    abstract protected fun isInContextImpl(element: PsiElement): Boolean

    class Generic : RustContextType("RUST_FILE", "Rust", EverywhereContextType::class.java) {
        override fun isInContextImpl(element: PsiElement): Boolean = true
    }

    class Struct : RustContextType("RUST_STRUCT", "Structure", Generic::class.java) {
        override fun isInContextImpl(element: PsiElement): Boolean =
            // Structs can't be nested or contain other expressions,
            // so it is ok to look for any Struct ancestor.
            PsiTreeUtil.getParentOfType(element, RustStructItem::class.java) != null
    }
}
