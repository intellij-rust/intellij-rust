package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import org.rust.lang.core.resolve.ref.RustReference

interface RustQualifiedReferenceElement : RustNamedElement {

    /**
     *  Returns `true` if this is a path starting at the crate root.
     *
     *  That is, if this path starts with `::` or if this path is from a use item
     *
     *  Example:
     *
     *    ```Rust
     *    use ::foo::bar;   // relative to root
     *    use foo::bar;     // relative to root, the same as the above
     *
     *    fn main() {
     *        ::foo::bar;   // relative to root
     *        foo::bar;     // relative to current module
     *    }
     *    ```
     *
     *  Reference:
     *    https://doc.rust-lang.org/reference.html#paths
     *    https://doc.rust-lang.org/reference.html#use-declarations
     */
    val isRelativeToCrateRoot: Boolean

    /**
     *  Returns true if this path references ancestor module via `self` and `super` chain.
     *
     *  Paths can contain any combination of identifiers and self and super keywords.
     *  However, a path is "well formed" only if it starts with `(self::)? (super::)*`.
     *  In other words, in `foo::super::bar` the `super` is meaningless and resolves
     *  to nothing.
     *
     *  This check returns true for `(self::)? (super::)*` part of a path.
     *
     *  Reference:
     *    https://doc.rust-lang.org/reference.html#paths
     */
    val isAncestorModulePrefix: Boolean

    /**
     * Returns true if this is a `self::` prefixed qualified-reference
     */
    val isSelf: Boolean

    val nameElement: PsiElement?

    val qualifier: RustQualifiedReferenceElement?

    override fun getReference(): RustReference
}
