package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.types.Ty

object RustUnknownType : Ty {
    override fun canUnifyWith(other: Ty, project: Project): Boolean = false

    override fun toString(): String = "<unknown>"
}
