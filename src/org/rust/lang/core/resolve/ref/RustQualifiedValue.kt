package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustNamedElement

public interface RustQualifiedValue : RustNamedElement {

    fun getSeparator(): PsiElement?

    fun getReferenceNameElement(): PsiElement?

    fun getQualifier(): RustQualifiedValue?

}
