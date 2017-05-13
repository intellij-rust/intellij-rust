package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsMacroBodySimpleMatching
import org.rust.lang.core.resolve.ref.RsMacroBodySimpleMatchingReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsMacroBodySimpleMatchingImplMixin(node: ASTNode) : RsCompositeElementImpl(node), RsMacroBodySimpleMatching {

    override fun getReference(): RsReference = RsMacroBodySimpleMatchingReferenceImpl(this)

    override val referenceName: String
        get() = referenceNameElement.text

    override val referenceNameElement: PsiElement
        get() = macroBinding ?: findChildByType(RsElementTypes.CRATE)!!
}
