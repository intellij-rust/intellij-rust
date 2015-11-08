package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.lexer.RustTokenElementTypes
import org.rust.lang.core.psi.RustPathPart
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.psi.RustQualifiedReferenceElement
import org.rust.lang.core.resolve.ref.RustReferenceImpl

abstract class RustPathPartImplMixin(node: ASTNode) : RustNamedElementImpl(node)
                                                    , RustQualifiedReferenceElement
                                                    , RustPathPart {

    override fun getReference(): RustReference = RustReferenceImpl(this)

    override fun getNameElement() = identifier

    override fun getSeparator(): PsiElement? = findChildByType(RustTokenElementTypes.COLONCOLON)

    override fun getQualifier(): RustQualifiedReferenceElement? =
        pathPart?.let {
            if (it.firstChild != null) it else null
        }

    override val isFullyQualified: Boolean
        get() = getQualifier() == null && getSeparator() != null
}
