/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project

data class TyTuple(val types: List<Ty>) : Ty {

    override fun canUnifyWith(other: Ty, project: Project, mapping: TypeMapping?): Boolean = merge(mapping) {
        other is TyTuple && types.size == other.types.size &&
            types.zip(other.types).all { (type1, type2) -> type1.canUnifyWith(type2, project, it) }
    }

    override fun substitute(subst: Substitution): TyTuple =
        TyTuple(types.map { it.substitute(subst) })

    override fun toString(): String = types.joinToString(", ", "(", ")")
}

