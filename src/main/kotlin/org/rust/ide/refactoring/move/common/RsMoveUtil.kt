/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move.common

import org.rust.ide.inspections.import.insertUseItem
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.ext.*

class RsMoveReferenceInfo(
    val pathOld: RsPath,
    val pathNew: RsPath?,
    // == `pathOld.reference.resolve()`
    val target: RsQualifiedNamedElement
) {
    val isInsideUseDirective: Boolean get() = pathOld.ancestorStrict<RsUseItem>() != null
}

fun RsPath.isAbsolute(): Boolean {
    if (basePath().hasColonColon) return true
    if (startsWithSuper()) return false

    val basePathTarget = basePath().reference?.resolve() as? RsMod ?: return false
    return basePathTarget.isCrateRoot
}

fun RsPath.startsWithSuper(): Boolean = basePath().referenceName == "super"

// like `text`, but without spaces and comments
// expected to be used for paths without type qualifiers and type arguments
val RsPath.textNormalized: String
    get() {
        val parts = listOfNotNull(path?.textNormalized, coloncolon?.text, referenceName)
        return parts.joinToString("")
    }

fun RsPath.resolvesToAndAccessible(target: RsQualifiedNamedElement): Boolean {
    val reference = reference ?: return false
    if (!reference.isReferenceTo(target)) return false

    for (subpath in generateSequence(this) { it.path }) {
        val subpathTarget = subpath.reference?.resolve() as? RsVisible ?: continue
        if (!subpathTarget.isVisibleFrom(containingMod)) return false
    }
    return true
}

// returns `super` instead of `this` for `RsFile`
// actually it is a bit inconsistent that `containingMod` for `RsMod`
// returns `super` when mod is `RsModItem` and `this` when mod is `RsFile`
val RsElement.containingModStrict: RsMod
    get() = when (this) {
        is RsMod -> `super` ?: this
        else -> containingMod
    }

fun addImport(psiFactory: RsPsiFactory, context: RsElement, usePath: String) {
    if (!usePath.contains("::")) return
    val blockScope = context.ancestors.find { it is RsBlock && it.childOfType<RsUseItem>() != null } as RsBlock?
    check(context !is RsMod)
    val scope = blockScope ?: context.containingMod
    scope.insertUseItem(psiFactory, usePath)
}
