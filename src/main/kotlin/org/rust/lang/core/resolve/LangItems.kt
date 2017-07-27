/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyEnum

// This is super hackish. Need to figure out how to
// identify known ty (See also the CString inspection).
// Java uses fully qualified names for this, perhaps we
// can do this as well? Will be harder to test though :(
fun isStdResult(type: Ty): Boolean {
    return type is TyEnum && type.item.name == "Result"
}
