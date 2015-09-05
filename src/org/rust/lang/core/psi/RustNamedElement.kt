package org.rust.lang.core.psi

import com.intellij.psi.PsiNamedElement

public interface RustNamedElement : PsiNamedElement {

    override fun getName(): String
}

