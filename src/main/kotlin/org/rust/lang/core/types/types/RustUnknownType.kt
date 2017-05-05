package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.types.RustType

object RustUnknownType : RustType {
    override fun canUnifyWith(other: RustType, project: Project): Boolean = false

    override fun toString(): String = "<unknown>"
}
