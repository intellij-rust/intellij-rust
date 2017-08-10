/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString
import org.rust.lang.core.resolve.ImplLookup

object TyUnknown : Ty {
    override fun unifyWith(other: Ty, lookup: ImplLookup): UnifyResult = UnifyResult.fail

    override fun toString(): String = tyToString(this)
}
