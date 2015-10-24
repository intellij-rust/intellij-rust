package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustPathPart
import org.rust.lang.core.resolve.ref.RustAbstractQualifiedReference
import org.rust.lang.core.resolve.ref.RustQualifiedReference

abstract class RustPathPartImplMixin(node: ASTNode) : RustAbstractQualifiedReference(node)
        , RustPathPart {

    override fun getSeparator(): PsiElement? = null

    override fun isSoft(): Boolean = false

    override fun getReferenceNameElement() = identifier

    override fun getVariants(): Array<out Any> {
        throw NotImplementedError()
    }
}
