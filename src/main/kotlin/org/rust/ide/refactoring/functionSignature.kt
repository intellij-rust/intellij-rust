/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.types.type

/**
 * Helper class for storing and formatting information about the signature of a function.
 */
abstract class RsFunctionSignatureConfig(val function: RsFunction) {
    protected val typeParametersText: String
        get() {
            val typeParams = typeParameters()
            if (typeParams.isEmpty()) return ""
            return typeParams.joinToString(separator = ", ", prefix = "<", postfix = ">") { it.text }
        }

    protected val whereClausesText: String
        get() {
            val wherePredList = function.whereClause?.wherePredList ?: return ""
            if (wherePredList.isEmpty()) return ""
            val typeParams = typeParameters().map { it.declaredType }
            if (typeParams.isEmpty()) return ""
            val filtered = wherePredList.filter { it.typeReference?.type in typeParams }
            if (filtered.isEmpty()) return ""
            return filtered.joinToString(separator = ", ", prefix = " where ") { it.text }
        }

    protected abstract fun typeParameters(): List<RsTypeParameter>
}
