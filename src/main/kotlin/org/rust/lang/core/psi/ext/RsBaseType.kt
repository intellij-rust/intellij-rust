/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsBaseType

val RsBaseType.isCself: Boolean get() {
    val path = path
    return path != null && !path.hasColonColon && path.hasCself
}

val RsBaseType.isUnit: Boolean get() = (stub?.isUnit) ?: (lparen != null && rparen != null)
val RsBaseType.isNever: Boolean get() = (stub?.isNever) ?: (excl != null)
val RsBaseType.isUnderscore: Boolean get() = (stub?.isUnderscore) ?: (underscore != null)

val RsBaseType.name: String? get() = path?.referenceName
