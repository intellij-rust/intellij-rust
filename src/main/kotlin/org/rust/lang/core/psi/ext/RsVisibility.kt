/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.ide.icons.addVisibilityIcon
import org.rust.lang.core.psi.*
import javax.swing.Icon

interface RsVisible : RsElement {
    val isPublic: Boolean
}

interface RsVisibilityOwner : RsVisible {
    val vis: RsVis?
}

fun RsVisibilityOwner.iconWithVisibility(flags: Int, icon: Icon): Icon =
    if ((flags and com.intellij.openapi.util.Iconable.ICON_FLAG_VISIBILITY) == 0)
        icon
    else
        icon.addVisibilityIcon(isPublic)

fun RsVisible.isVisibleFrom(mod: RsMod): Boolean {
    if (isPublic) return true

    val elementMod = (if (this is RsMod) this.`super` else this.contextStrict()) ?: return true
    // We have access to any item in any super module of `mod`
    // Note: `mod.superMods` contains `mod`
    if (mod.superMods.contains(elementMod)) return true
    if (mod is RsFile && mod.originalFile == elementMod) return true

    val members = this.context as? RsMembers ?: return false
    val parent = members.context ?: return true
    return when {
        // Associated items in a pub Trait are public by default
        parent is RsImplItem && parent.traitRef != null -> parent.traitRef?.resolveToTrait?.isPublic ?: true
        parent is RsTraitItem && parent.isPublic -> true
        else -> false
    }
}
