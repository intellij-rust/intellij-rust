/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsValueParameter


val RsValueParameter.patText: String? get() {
    val stub = stub
    return if (stub != null) stub.patText else pat?.text
}

val RsValueParameter.typeReferenceText: String? get() {
    val stub = stub
    return if (stub != null) stub.typeReferenceText else typeReference?.text
}
