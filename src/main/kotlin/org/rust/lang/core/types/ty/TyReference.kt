/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project

data class TyReference(val referenced: Ty, val mutable: Boolean = false) : Ty {

    override fun canUnifyWith(other: Ty, project: Project, mapping: TypeMapping?): Boolean = merge(mapping) {
        other is TyReference && referenced.canUnifyWith(other.referenced, project, it)
    }

    override fun toString(): String = "${if (mutable) "&mut " else "&"}$referenced"

    override fun substitute(map: TypeArguments): Ty =
        TyReference(referenced.substitute(map), mutable)
}
