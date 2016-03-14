package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustPathPart
import org.rust.lang.core.psi.RustQualifiedReferenceElement
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.RustViewPath
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.resolve.ref.RustQualifiedReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference

abstract class RustPathPartImplMixin(node: ASTNode) : RustNamedElementImpl(node)
                                                    , RustQualifiedReferenceElement
                                                    , RustPathPart {

    override fun getReference(): RustReference = RustQualifiedReferenceImpl(this)

    override val nameElement: PsiElement?
        get() = identifier ?: self ?: `super`

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

    override val isAncestorModulePrefix: Boolean
        get() {
            val qual = qualifier
            if (qual != null) {
                return `super` != null && qual.isAncestorModulePrefix
            }
            val isFullyQualified = separator != null
            if (isFullyQualified) {
                return false
            }
            // `self` by itself is not a module prefix, it's and identifier.
            // So for `self` we need to check that it is not the only segment of path.
            return `super` != null || (self != null && nextSibling != null)
        }

    override val isSelf: Boolean
        get() = self != null
}
