/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project

data class TySlice(val elementType: Ty) : Ty {
    override fun canUnifyWith(other: Ty, project: Project, mapping: TypeMapping?): Boolean = merge(mapping) {
        other is TySlice && elementType.canUnifyWith(other.elementType, project, it) ||
            other is TyArray && elementType.canUnifyWith(other.base, project, it)
    }

    override fun substitute(map: TypeArguments): Ty {
        return TySlice(elementType.substitute(map))
    }

    override fun toString() = "[$elementType]"
}
