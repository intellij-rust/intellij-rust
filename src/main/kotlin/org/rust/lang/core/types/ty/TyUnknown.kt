package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project

object TyUnknown : Ty {
    override fun canUnifyWith(other: Ty, project: Project, mapping: TypeMapping?): Boolean = false

    override fun toString(): String = "<unknown>"
}
