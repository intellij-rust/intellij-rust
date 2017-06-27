/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsBlockFields
import org.rust.lang.core.psi.RsFieldDecl
import org.rust.lang.core.psi.RsTupleFieldDecl
import org.rust.lang.core.psi.RsTupleFields

interface RsFieldsOwner {
    val blockFields: RsBlockFields?
    val tupleFields: RsTupleFields?
}

val RsFieldsOwner.namedFields: List<RsFieldDecl>
    get() = blockFields?.fieldDeclList.orEmpty()

val RsFieldsOwner.positionalFields: List<RsTupleFieldDecl>
    get() = tupleFields?.tupleFieldDeclList.orEmpty()
