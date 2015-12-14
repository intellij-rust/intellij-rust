package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.lexer.RustTokenElementTypes
import org.rust.lang.core.psi.RustPathPart
import org.rust.lang.core.psi.RustQualifiedReferenceElement
import org.rust.lang.core.psi.RustViewPath
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.resolve.ref.RustQualifiedReferenceImpl

abstract class RustPathPartImplMixin(node: ASTNode) : RustNamedElementImpl(node)
                                                    , RustQualifiedReferenceElement
                                                    , RustPathPart {

    override fun getReference(): RustReference = RustQualifiedReferenceImpl(this)

    override val nameElement: PsiElement?
        get() = identifier

    override val separator: PsiElement?
        get() = findChildByType(RustTokenElementTypes.COLONCOLON)

    override val qualifier: RustQualifiedReferenceElement?
        get() = if (pathPart?.firstChild != null) pathPart else null

    private val isViewPath: Boolean
        get() {
            val parent = parent
            return when (parent) {
                is RustViewPath          -> true
                is RustPathPartImplMixin -> parent.isViewPath
                else                     -> false
            }
        }

    override val isFullyQualified: Boolean
        get() {
            val qual = qualifier
            return if (qual == null) {
                separator != null || (isViewPath && self == null && `super` == null)
            } else {
                qual.isFullyQualified
            }
        }

    override val isModulePrefix: Boolean
        get() {
            val qual = qualifier
            return if (qual == null) {
                separator == null && (self != null || `super` != null)
            } else {
                `super` != null && qual.isModulePrefix
            }
        }

    override val isSelf: Boolean
        get() = self != null
}
