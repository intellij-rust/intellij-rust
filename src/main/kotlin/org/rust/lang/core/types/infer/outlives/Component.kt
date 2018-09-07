/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer.outlives

import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*

sealed class Component {
    data class Region(val value: org.rust.lang.core.types.regions.Region) : Component()
    data class Parameter(val value: TyTypeParameter) : Component()
    data class UnresolvedInferenceVariable(val value: TyInfer) : Component()
    data class Projection(val value: TyProjection) : Component()
}

/**
 * Returns all the things that must outlive `'a` for the condition `[ty]: 'a` to hold.
 * Note that [ty] must be a **fully resolved type**.
 */
fun getOutlivesComponents(ty: Ty): List<Component> {
    val components = mutableListOf<Component>()
    computeComponents(ty, components)
    return components
}

/** Descend through the types, looking for the various "base" components and collecting them into [out]. */
fun computeComponents(ty: Ty, out: MutableList<Component>) {
    when (ty) {
        is TyTypeParameter -> out.add(Component.Parameter(ty))
        is TyProjection -> out.add(Component.Projection(ty))
        is TyInfer -> out.add(Component.UnresolvedInferenceVariable(ty))
        else -> {
            pushRegionConstraints(out, ty.regions)
            for (subTy in ty.walkShallow()) {
                computeComponents(subTy, out)
            }
        }
    }
}

private fun pushRegionConstraints(out: MutableList<Component>, regions: Collection<Region>) =
    regions.mapTo(out) { Component.Region(it) }
