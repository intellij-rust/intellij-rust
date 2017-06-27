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
    override fun getText() = "Set reference immutable"
    override val mutable = false
}
