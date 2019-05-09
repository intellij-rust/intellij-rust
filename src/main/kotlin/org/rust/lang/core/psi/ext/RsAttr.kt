/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsInnerAttr
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsOuterAttr

interface RsAttr : RsElement {
    val metaItem: RsMetaItem
}

val RsAttr.owner: RsDocAndAttributeOwner?
    get() = when (this) {
        is RsInnerAttr -> parent?.parent as? RsDocAndAttributeOwner
        is RsOuterAttr -> parent as? RsDocAndAttributeOwner
        // Throw exception so that any problems with this property aren't silent and are instead easily findable
        else -> error("Unsupported attribute type: $this")
    }
