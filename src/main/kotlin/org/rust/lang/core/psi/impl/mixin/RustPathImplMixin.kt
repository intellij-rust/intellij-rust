package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.core.resolve.ref.RustQualifiedReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.symbols.RustQualifiedPath
import org.rust.lang.core.symbols.RustQualifiedPathPart
import org.rust.lang.core.symbols.impl.RustCSelfQualifiedPathPart
import org.rust.lang.core.symbols.impl.RustNamedQualifiedPathPart
import org.rust.lang.core.symbols.impl.RustSelfQualifiedPathPart
import org.rust.lang.core.symbols.impl.RustSuperQualifiedPathPart

abstract class RustPathImplMixin(node: ASTNode) : RustCompositeElementImpl(node)
                                                  , RustQualifiedReferenceElement
                                                  , RustPathElement
                                                  , RustQualifiedPath {

    override fun getReference(): RustReference = RustQualifiedReferenceImpl(this)

    override val referenceNameElement: PsiElement
        get() = checkNotNull(identifier ?: self ?: `super` ?: cself) {
            "Path must contain identifier: $this ${this.text} at ${this.containingFile.virtualFile.path}"
        }

    override val qualifier: RustQualifiedReferenceElement?
        get() = path

    override val part: RustQualifiedPathPart
        get() =
            identifier  ?.let { RustNamedQualifiedPathPart(it.text) }   ?:
            self        ?.let { RustSelfQualifiedPathPart }             ?:
            cself       ?.let { RustCSelfQualifiedPathPart }            ?:
            `super`     ?.let { RustSuperQualifiedPathPart }            ?: throw IllegalStateException("Panic at the disco!")


    override val fullyQualified: Boolean
        get() = qualifier?.fullyQualified ?: separator != null || isViewPath && self == null && `super` == null

    override val relativeModulePrefix: RelativeModulePrefix
        get() = seekRelativeModulePrefixInternal(nextSibling != null)

    private val separator: PsiElement?
        get() = findChildByType(RustTokenElementTypes.COLONCOLON)

    /**
     *  Checks if this path references ancestor module via `self` and `super` chain.
     *
     *  Paths can contain any combination of identifiers and self and super keywords.
     *  However, a path is "well formed" only if it starts with `(self::)? (super::)*`.
     *
     *  So there are three possible outcomes:
     *    + this is not a relative module reference at all (`::foo::bar`)
     *    + this is an invalid path (`foo::self`)
     *    + this is a path to nth ancestor (`self::super`)
     *
     *  Reference:
     *    https://doc.rust-lang.org/reference.html#paths
     */
    private val isViewPath: Boolean get() {
        val parent = parent
        return parent is RustUseItemElement || (parent is RustPathImplMixin && parent.isViewPath)
    }

}

