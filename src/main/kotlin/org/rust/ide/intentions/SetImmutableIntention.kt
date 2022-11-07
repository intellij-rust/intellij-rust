/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.RsBundle

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
    override fun getText() = RsBundle.message("intention.Rust.SetImmutable.text")
    override val mutable = false
}
