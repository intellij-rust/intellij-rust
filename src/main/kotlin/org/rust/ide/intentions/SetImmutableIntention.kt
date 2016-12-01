package org.rust.ide.intentions

/**
 * Set reference immutable
 *
 * ```
 * &mut type
 * ```
 *
 * to this:
 *
 * ```
 * &type
 * ```
 */
class SetImmutableIntention : SetMutableIntention() {
    override fun getText() = "Set reference immutable"
    override val mutable = false
}
