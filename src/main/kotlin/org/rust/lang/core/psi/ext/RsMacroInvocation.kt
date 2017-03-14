package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsMacroInvocation
import org.rust.lang.core.resolve.ref.RsMacroReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference


abstract class RsMacroInvocationImplMixin(node: ASTNode) : RsCompositeElementImpl(node), RsMacroInvocation {

    override fun getReference(): RsReference = RsMacroReferenceImpl(this)

    override val referenceName: String
        get() = referenceNameElement.text

    override val referenceNameElement: PsiElement
        get() = findChildByType(IDENTIFIER)!!

}

