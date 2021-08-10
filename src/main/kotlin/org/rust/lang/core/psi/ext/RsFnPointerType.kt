/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsFnPointerType
import org.rust.lang.core.psi.RsValueParameter
import org.rust.lang.core.stubs.RsFnPointerTypeStub

val RsFnPointerType.valueParameters: List<RsValueParameter>
    get() = valueParameterList?.valueParameterList.orEmpty()

val RsFnPointerType.isUnsafe: Boolean
    get() {
        val stub = greenStub as? RsFnPointerTypeStub
        return stub?.isUnsafe ?: (unsafe != null)
    }

val RsFnPointerType.isExtern: Boolean
    get() {
        val stub = greenStub as? RsFnPointerTypeStub
        return stub?.isExtern ?: (externAbi != null)
    }

val RsFnPointerType.abiName: String?
    get() {
        val stub = greenStub as? RsFnPointerTypeStub
        return stub?.abiName ?: externAbi?.stringLiteral?.text
    }
