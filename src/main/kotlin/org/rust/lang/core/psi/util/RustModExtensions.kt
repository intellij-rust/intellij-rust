package org.rust.lang.core.psi.util

import org.rust.lang.core.names.RustFileModuleId
import org.rust.lang.core.names.RustQualifiedName
import org.rust.lang.core.names.parts.RustIdNamePart
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.containingMod
import org.rust.lang.core.psi.impl.modulePath


object RustModules {
    val MOD_RS  = "mod.rs"
}


val RustMod.canonicalNameInFile: RustQualifiedName?
    get() =
        when (containingMod) {
            null -> containingFile.modulePath?.let  { RustFileModuleId(it) }
            else -> name?.let                       { RustQualifiedName(RustIdNamePart(it), `super`?.canonicalNameInFile) }
        }
