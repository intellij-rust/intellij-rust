/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.util.CachedValueProvider
import com.intellij.util.SmartList
import com.intellij.util.recursionSafeLazy
import gnu.trove.THashMap
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.infer.constGenerics
import org.rust.lang.core.types.infer.generics
import org.rust.lang.core.types.rawType
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import kotlin.LazyThreadSafetyMode.PUBLICATION

/**
 * Used for optimization purposes, to reduce access to cache and PSI tree in some very hot places,
 * [ImplLookup.assembleCandidates] and [processAssociatedItems] in particular
 */
class RsCachedImplItem(
    val impl: RsImplItem
) {
    private val traitRef: RsTraitRef? = impl.traitRef
    val containingCrate: Crate?
    val containingCrates: List<Crate>
    val isValid: Boolean
    val isNegativeImpl: Boolean = impl.isNegativeImpl

    init {
        val (isValid, crate, crates) = impl.isValidProjectMemberAndContainingCrate
        this.containingCrate = crate
        this.containingCrates = crates
        this.isValid = isValid && !impl.isReservationImpl
    }

    val isInherent: Boolean get() = traitRef == null

    val implementedTrait: BoundElement<RsTraitItem>? by recursionSafeLazy { traitRef?.resolveToBoundTrait() }
    val typeAndGenerics: Triple<Ty, List<TyTypeParameter>, List<CtConstParameter>>? by lazy(PUBLICATION) {
        impl.typeReference?.rawType?.let { Triple(it, impl.generics, impl.constGenerics) }
    }

    /** For `impl T for Foo` returns union of impl members and trait `T` members that are not overriden by the impl */
    val implAndTraitExpandedMembers: Map<String, List<RsAbstractable>> by lazy(PUBLICATION) {
        val membersMap = THashMap<String, MutableList<RsAbstractable>>()
        for (member in impl.members?.expandedMembers.orEmpty()) {
            val name = member.name ?: continue
            membersMap.getOrPut(name) { SmartList() }.add(member)
        }
        val traitMembers = implementedTrait?.element?.members?.expandedMembers
            ?: return@lazy membersMap
        val implMemberNames = HashSet<String>(membersMap.keys)
        for (member in traitMembers) {
            val name = member.name ?: continue
            if (name in implMemberNames) continue
            membersMap.getOrPut(name) { SmartList() }.add(member)
        }
        membersMap
    }

    // Reduces heap memory usage by reducing number on `TraitImplSource.ExplicitImpl` instances
    val explicitImpl: TraitImplSource.ExplicitImpl = TraitImplSource.ExplicitImpl(this)

    companion object {
        fun forImpl(impl: RsImplItem): RsCachedImplItem {
            return (impl as RsImplItemImplMixin).cachedImplItem.value
        }

        fun <T> toCachedResult(psi: RsElement, containingCrate: Crate?, cachedImpl: T): CachedValueProvider.Result<T> {
            val containingFile = psi.containingFile
            val modTracker = if (containingCrate?.origin == PackageOrigin.WORKSPACE) {
                containingFile.project.rustStructureModificationTracker
            } else {
                containingFile.project.rustPsiManager.rustStructureModificationTrackerInDependencies
            }
            val deps = if (!containingFile.isPhysical || containingFile.virtualFile is VirtualFileWindow) {
                // Non-physical PSI does not have event system, but we can track the file changes
                listOf(modTracker, ModificationTracker { containingFile.modificationStamp })
            } else {
                listOf(modTracker)
            }
            return CachedValueProvider.Result.create(cachedImpl, deps)
        }
    }
}
