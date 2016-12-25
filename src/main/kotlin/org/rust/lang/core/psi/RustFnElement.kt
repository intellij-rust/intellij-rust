package org.rust.lang.core.psi

import com.intellij.psi.PsiElement

interface RustFnElement : RustNamedElement,
                          RustGenericDeclaration,
                          RustInnerAttributeOwner,
                          RustOuterAttributeOwner {
    val identifier: PsiElement
    val parameters: RustParametersElement?
    val block: RustBlockElement?
    val retType: RustRetTypeElement?

    val isAbstract: Boolean
    val isStatic: Boolean
    val isTest: Boolean
}

