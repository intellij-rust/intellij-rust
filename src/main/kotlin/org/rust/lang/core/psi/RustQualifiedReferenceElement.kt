package org.rust.lang.core.psi

interface RustQualifiedReferenceElement : RustReferenceElement {

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
    val relativeModulePrefix: RelativeModulePrefix

    val qualifier: RustQualifiedReferenceElement?
}

sealed class RelativeModulePrefix {
    object Invalid: RelativeModulePrefix()
    object NotRelative: RelativeModulePrefix()
    class AncestorModule(val level: Int): RelativeModulePrefix() {
        init { require(level >= 0) }
    }
}
