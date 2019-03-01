/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.ide.icons.addVisibilityIcon
import org.rust.lang.core.psi.*
import javax.swing.Icon

interface RsVisible : RsElement {
    val visibility: RsVisibility
}

interface RsVisibilityOwner : RsVisible {
    val vis: RsVis?

    @JvmDefault
    override val visibility: RsVisibility
        get() = vis?.visibility ?: RsVisibility.Private
}

val RsVisible.isPublic get() = visibility != RsVisibility.Private

fun RsVisibilityOwner.iconWithVisibility(flags: Int, icon: Icon): Icon =
    if ((flags and com.intellij.openapi.util.Iconable.ICON_FLAG_VISIBILITY) == 0)
        icon
    else
        icon.addVisibilityIcon(isPublic)

fun RsVisible.isVisibleFrom(mod: RsMod): Boolean {
    val elementMod = when (val visibility = visibility) {
        RsVisibility.Public -> return true
        RsVisibility.Private -> (if (this is RsMod) this.`super` else this.contextStrict()) ?: return true
        is RsVisibility.Restricted -> visibility.inMod
    }

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

enum class RsVisStubKind {
    PUB, CRATE, RESTRICTED
}

val RsVis.stubKind: RsVisStubKind
    get() = stub?.kind ?: when {
        crate != null -> RsVisStubKind.CRATE
        visRestriction != null -> RsVisStubKind.RESTRICTED
        else -> RsVisStubKind.PUB
    }

sealed class RsVisibility {
    object Private : RsVisibility()
    object Public : RsVisibility()
    data class Restricted(val inMod: RsMod) : RsVisibility()
}

val RsVis.visibility: RsVisibility
    get() = when (stubKind) {
        RsVisStubKind.PUB -> RsVisibility.Public
        RsVisStubKind.CRATE -> crateRoot?.let { RsVisibility.Restricted(it) } ?: RsVisibility.Public
        RsVisStubKind.RESTRICTED -> {
            val restrictedIn = visRestriction!!.path.reference.resolve() as? RsMod
            if (restrictedIn != null) RsVisibility.Restricted(restrictedIn) else  RsVisibility.Public
        }
    }
