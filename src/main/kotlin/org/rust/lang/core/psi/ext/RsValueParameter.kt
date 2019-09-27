/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.*


val RsValueParameter.patText: String?
    get() {
        val stub = greenStub
        return if (stub != null) stub.patText else pat?.presentableText
    }

private val RsPat.presentableText: String
    get() = when (this) {
        is RsPatTup -> patList.joinToString(separator = ", ", prefix = "(", postfix = ")") { it.presentableText }

        is RsPatSlice -> patList.joinToString(separator = ", ", prefix = "[", postfix = "]") { it.presentableText }

        is RsPatStruct -> patFieldList.joinToString(separator = ", ", prefix = "${path.text} {", postfix = "}") { it.presentableText }

        is RsPatTupleStruct -> patList.joinToString(separator = ", ", prefix = "${path.text}(", postfix = ")") { it.presentableText }

        is RsPatIdent -> patBinding.identifier.text

        else -> text
    }

private val RsPatField.presentableText: String
    get() = patBinding?.identifier?.text ?: patFieldFull?.identifier?.text ?: ""
