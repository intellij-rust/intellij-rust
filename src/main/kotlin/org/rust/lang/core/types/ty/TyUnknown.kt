/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString

object TyUnknown : Ty() {
    override fun toString(): String = tyToString(this)
}
