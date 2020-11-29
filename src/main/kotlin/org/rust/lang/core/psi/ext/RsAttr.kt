/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.*

interface RsAttr : RsElement {
    val metaItem: RsMetaItem
}

val RsAttr.owner: RsDocAndAttributeOwner?
    get() = when (this) {
        is RsOuterAttr -> parent as? RsDocAndAttributeOwner
        is RsInnerAttr -> when (val parent = parent) {
            is RsMembers -> parent.parent as? RsDocAndAttributeOwner
            is RsBlock -> when (val parentParent = parent.parent) {
                is RsFunction -> parentParent
                else -> parent as? RsDocAndAttributeOwner
            }
            else -> parent as? RsDocAndAttributeOwner
        }
        // Throw exception so that any problems with this property aren't silent and are instead easily findable
        else -> error("Unsupported attribute type: $this")
    }
