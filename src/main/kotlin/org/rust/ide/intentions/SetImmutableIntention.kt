package org.rust.ide.intentions

/**
 * Set reference immutable in a parameter of a function declaration
 *
 * ```
 * fn func(param: &mut type)
 * ```
 *
 * to this:
 *
 * ```
 * fn func(param: &type)
 * ```
 */
class SetImmutableIntention : SetMutableIntention() {
    override fun getText() = "Set reference immutable"
    override val mutable = false
}
