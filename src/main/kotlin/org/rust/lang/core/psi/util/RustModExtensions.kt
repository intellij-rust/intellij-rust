package org.rust.lang.core.psi.util

import org.rust.lang.core.names.RustAnonymousId
import org.rust.lang.core.names.RustFileModuleId
import org.rust.lang.core.names.RustQualifiedName
import org.rust.lang.core.names.parts.RustIdNamePart
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.containingMod
import org.rust.lang.core.psi.impl.modulePath


object RustModules {
    val MOD_RS  = "mod.rs"
}

/**
 * Seals down canonical-path inside the module-tree of the particular
 * crate
 *
 * NOTE: That this is unique (since its a _tree_) for any particular module
 */
val RustModItem.canonicalName: RustQualifiedName?
    get() =
        when (isCrateRoot) {
            true -> RustAnonymousId
            else -> name?.let { RustQualifiedName(RustIdNamePart(it), `super`?.canonicalName) }
        }


val RustModItem.canonicalNameInFile: RustQualifiedName?
    get() =
        when (containingMod) {
            null -> containingFile.modulePath?.let  { RustFileModuleId(it) }
            else -> name?.let                       { RustQualifiedName(RustIdNamePart(it), `super`?.canonicalNameInFile) }
        }
