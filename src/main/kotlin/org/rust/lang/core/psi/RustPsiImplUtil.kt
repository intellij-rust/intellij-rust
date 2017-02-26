package org.rust.lang.core.psi

import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.stubs.RsVisibilityStub
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.symbols.RustPathSegment

/**
 * Mixin methods to implement PSI interfaces without copy pasting and
 * introducing monster base classes. Can be simplified when Kotlin supports
 * default methods in interfaces with mixed Kotlin-Java hierarchies (KT-9073 ).
 */
object RustPsiImplUtil {
    fun isPublic(psi: RsVisibilityOwner, stub: RsVisibilityStub?): Boolean =
        stub?.isPublic ?: isPublicNonStubbed(psi)

    fun isPublicNonStubbed(element: RsVisibilityOwner): Boolean =
        element.vis != null

    fun crateRelativePath(element: RsNamedElement): RustPath.CrateRelative? {
        val segment = element.name?.let { RustPathSegment.withoutGenerics(it) } ?: return null
        return element.containingMod?.crateRelativePath?.join(segment)
    }

    fun modCrateRelativePath(mod: RsMod): RustPath.CrateRelative? {
        val segments = mod.superMods.asReversed().drop(1).map {
            RustPathSegment.withoutGenerics(it.modName ?: return null)
        }
        return RustPath.CrateRelative(segments)
    }
}

