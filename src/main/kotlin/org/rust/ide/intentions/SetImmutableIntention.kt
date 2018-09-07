/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

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
    override val mutable: Boolean = false

    override fun getText(): String = "Set reference immutable"
}
