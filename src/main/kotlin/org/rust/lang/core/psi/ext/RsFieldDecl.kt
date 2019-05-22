/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsTypeReference

val RsFieldDecl.parentStruct: RsFieldsOwner? get() = stubAncestorStrict()

interface RsFieldDecl : RsOuterAttributeOwner, RsVisibilityOwner {
    val typeReference: RsTypeReference?
}
