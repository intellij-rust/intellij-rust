/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project

class TyArray(val base: Ty, val size: Int) : Ty {
    override fun canUnifyWith(other: Ty, project: Project, mapping: TypeMapping?): Boolean = merge(mapping) {
        other is TyArray && size == other.size && base.canUnifyWith(other.base, project, it)
    }

    override fun substitute(subst: Substitution): Ty =
        TyArray(base.substitute(subst), size)

    override fun toString() = "[$base; $size]"
}
