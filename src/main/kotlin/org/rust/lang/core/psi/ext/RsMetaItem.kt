/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsMetaItemArgs
import org.rust.lang.core.psi.RsTraitItem

/**
 * Returns identifier name if path inside meta item consists only of this identifier.
 * Otherwise, returns `null`
 */
val RsMetaItem.name: String? get() {
    val path = path ?: return null
    if (path.hasColonColon) return null
    return path.referenceName
}

val RsMetaItem.id: String?
    get() = generateSequence(path) { it.path }
        .asIterable()
        .reversed()
        .takeIf { it.isNotEmpty() }
        ?.map { it.referenceName ?: return null }
        ?.joinToString("::")

val RsMetaItem.value: String? get() = litExpr?.stringValue

val RsMetaItem.hasEq: Boolean get() = greenStub?.hasEq ?: (eq != null)

fun RsMetaItem.resolveToDerivedTrait(): RsTraitItem? =
    path?.reference?.resolve() as? RsTraitItem

/**
 * In the case of `#[foo(bar)]`, the `foo(bar)` meta item is considered "root" but `bar` is not.
 * In the case of `#[cfg_attr(windows, foo(bar))]`, the `foo(bar)` is also considered "root" meta item
 * because after `cfg_attr` expanding the `foo(bar)` will turn into `#[foo(bar)]`.
 * This also applied to nested `cfg_attr`s, e.g. `#[cfg_attr(windows, cfg_attr(foobar, foo(bar)))]`
 */
val RsMetaItem.isRootMetaItem: Boolean
    get() = parent is RsAttr || isCfgAttrBody

/**
 * ```
 * #[cfg_attr(condition, attr)]
 *                     //^
 * ```
 */
private val RsMetaItem.isCfgAttrBody: Boolean
    get() {
        val parent = parent as? RsMetaItemArgs ?: return false
        val parentMetaItem = parent.parent as? RsMetaItem ?: return false

        if (!parentMetaItem.isCfgAttrMetaItem) return false

        val conditionPart = parent.metaItemList.firstOrNull()
        return this != conditionPart
    }

/** `#[cfg_attr()]` */
private val RsMetaItem.isCfgAttrMetaItem: Boolean
    get() = name == "cfg_attr" && isRootMetaItem
