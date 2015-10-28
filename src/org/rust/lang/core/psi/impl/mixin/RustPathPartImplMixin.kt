package org.rust.lang.core.psi.impl.mixin

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.rust.lang.core.psi.RustPathPart
import org.rust.lang.core.resolve.ref.RustQualifiedValue
import org.rust.lang.core.resolve.ref.RustReference

abstract class RustPathPartImplMixin(node: ASTNode) : ASTWrapperPsiElement(node)
        , RustQualifiedValue
        , RustPathPart {

    override fun getSeparator(): PsiElement? = null

    override fun getReference(): PsiReference = RustReference(this)

    override fun getReferenceNameElement() = identifier

    override fun getQualifier(): RustQualifiedValue? = findChildByClass(javaClass)

    override fun setName(name: String): PsiElement? {
        throw UnsupportedOperationException()
    }

}
