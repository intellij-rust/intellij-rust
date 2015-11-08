package org.rust.lang.core.psi

import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement

public interface RustNamedElement   : RustCompositeElement
                                    , PsiNamedElement
                                    , NavigatablePsiElement {

    fun getNameElement(): PsiElement?

}

