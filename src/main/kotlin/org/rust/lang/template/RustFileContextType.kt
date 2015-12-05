package org.rust.lang.template

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import org.rust.lang.RustLanguage


class RustFileContextType : TemplateContextType("RUST_FILE", "Rust file") {
    override fun isInContext(file: PsiFile, offset: Int): Boolean =
        PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(RustLanguage)
}
