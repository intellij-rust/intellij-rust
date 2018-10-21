/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.resolve.TYPES
import org.rust.lang.core.resolve.processNestedScopesUpwards

interface RsItemElement : RsVisibilityOwner, RsOuterAttributeOwner, RsExpandedElement

fun <T : RsItemElement> Iterable<T>.filterInScope(scope: RsElement): List<T> {
    val set = toMutableSet()
    processNestedScopesUpwards(scope, {
        set.remove(it.element)
        set.isEmpty()
    }, TYPES)
    return if (set.isEmpty()) toList() else toMutableList().apply { removeAll(set) }
}
