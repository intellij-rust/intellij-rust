package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.lexer.RustTokenElementTypes
import org.rust.lang.core.psi.RustPathPart
import org.rust.lang.core.psi.RustQualifiedReferenceElement
import org.rust.lang.core.psi.RustViewPath
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.resolve.ref.RustReference
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

    private val isViewPath: Boolean
        get() {
            val parent = parent
            return when (parent) {
                is RustViewPath          -> true
                is RustPathPartImplMixin -> parent.isViewPath
                else                     -> false
            }
        }


    /**
     *  Returns `true` if this is a fully qualified path.
     *
     *  Paths in use items are special, they are implicitly FQ.
     *
     *  Example:
     *
     *    ```Rust
     *    use ::foo::bar;   // FQ
     *    use foo::bar;     // FQ, the same as the above
     *
     *    fn main() {
     *        ::foo::bar;   // FQ
     *        foo::bar;     // not FQ
     *    }
     *    ```
     *
     *  Reference:
     *    https://doc.rust-lang.org/reference.html#paths
     *    https://doc.rust-lang.org/reference.html#use-declarations
     */
    override val isFullyQualified: Boolean
        get() {
            val qual = getQualifier()
            return if (qual == null) {
                getSeparator() != null || (isViewPath && self == null && `super` == null)
            } else {
                qual.isFullyQualified
            }
        }
}
