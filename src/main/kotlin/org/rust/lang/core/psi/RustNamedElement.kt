package org.rust.lang.core.psi

import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiNamedElement

interface RustNamedElement : RustCompositeElement,
                             PsiNamedElement,
                             NavigatablePsiElement

