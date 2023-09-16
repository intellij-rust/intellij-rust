/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.createFromUsage

import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.ext.*

fun getVisibility(target: RsMod, source: RsMod): String = when {
    source.containingCrate != target.containingCrate -> "pub "
    source != target -> "pub(crate) "
    else -> ""
}

private fun getWritablePathTarget(path: RsPath): RsElement? {
    return path.qualifier?.reference?.resolve()
}

/**
 * `foo::bar`
 *  ~~~~~~~~ [path]
 *  ~~~ returning mod
 */
fun getWritablePathMod(path: RsPath): RsMod? {
    if (path.qualifier == null) return path.containingMod
    return getWritablePathTarget(path) as? RsMod
}

/**
 * Find either a module, or an ADT which qualifies the passed path.
 */
fun getTargetItemForFunctionCall(path: RsPath): RsElement? {
    val qualifier = path.qualifier ?: return path.containingMod

    if (qualifier.hasCself && !qualifier.hasColonColon) {
        val impl = qualifier.reference?.resolve() as? RsImplItem
        if (impl != null && impl.isContextOf(path)) return impl
    }
    return getWritablePathTarget(path)
}
