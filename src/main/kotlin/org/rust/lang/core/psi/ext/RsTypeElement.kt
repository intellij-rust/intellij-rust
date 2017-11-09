/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.*


interface RsTypeElement : RsElement

val RsTypeElement.owner: RsTypeReference
    get() = ancestors
        .drop(1)
        .filterNot { it is RsTypeArgumentList || it is RsPath }
        .takeWhile { it is RsBaseType || it is RsTupleType || it is RsRefLikeType || it is RsTypeReference }
        .last() as RsTypeReference

