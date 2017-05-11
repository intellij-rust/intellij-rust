package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.types.Ty

object RustUnitType : Ty {

    override fun canUnifyWith(other: Ty, project: Project): Boolean =
        other is RustUnitType

    override fun toString(): String = "()"
}

