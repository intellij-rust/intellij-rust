/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.resolve.ImplLookup

object TyUnknown : Ty {
    override fun canUnifyWith(other: Ty, lookup: ImplLookup, mapping: TypeMapping?): Boolean = false

    override fun toString(): String = "<unknown>"
}
