package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustPathExpr
import org.rust.lang.core.psi.RustPathExprPart
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.resolve.ref.RustAbstractQualifiedReference
import org.rust.lang.core.resolve.ref.RustReference

public abstract class RustPathExprPartImplMixin(node: ASTNode) : RustAbstractQualifiedReference(node)
                                                               , RustPathExprPart {

    override fun getSeparator(): PsiElement? = getColoncolon()

    override fun getReferenceNameElement(): PsiElement? = getIdentifier()

    override fun isSoft(): Boolean = false

    override fun getVariants(): Array<out Any> {
        throw UnsupportedOperationException()
    }

}

