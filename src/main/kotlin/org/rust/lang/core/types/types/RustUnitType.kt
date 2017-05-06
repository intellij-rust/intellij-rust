package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.types.RustType

object RustUnitType : RustType {

    override fun canUnifyWith(other: RustType, project: Project): Boolean =
        other is RustUnitType

    override fun toString(): String = "()"
}

