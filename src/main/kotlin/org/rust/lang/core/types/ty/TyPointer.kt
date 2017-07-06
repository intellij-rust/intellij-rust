/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project

data class TyPointer(val referenced: Ty, val mutable: Boolean = false) : Ty {

    override fun canUnifyWith(other: Ty, project: Project, mapping: TypeMapping?): Boolean = merge(mapping) {
        other is TyPointer && referenced.canUnifyWith(other.referenced, project, it)
    }

    override fun toString() = "*${if (mutable) "mut" else "const"} $referenced"

    override fun substitute(subst: Substitution): Ty =
        TyPointer(referenced.substitute(subst), mutable)
}
