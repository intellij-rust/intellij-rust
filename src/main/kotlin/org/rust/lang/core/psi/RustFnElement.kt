package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import org.rust.lang.core.resolve.scope.RustResolveScope

interface RustFnElement : RustNamedElement,
                          RustGenericDeclaration,
                          RustResolveScope,
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

