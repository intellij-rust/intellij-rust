package org.rust.lang.core.psi.impl.mixin

import com.intellij.extapi.psi.ASTWrapperPsiElement.EMPTY_ARRAY
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustPathPart
import org.rust.lang.core.resolve.ref.RustAbstractQualifiedReference

abstract class RustPathPartImplMixin(node: ASTNode) : RustAbstractQualifiedReference(node)
        , RustPathPart {

    override fun getSeparator(): PsiElement? = null

    override fun isSoft(): Boolean = false

    override fun getReferenceNameElement() = identifier

    override fun getVariants(): Array<out Any> = EMPTY_ARRAY
}
