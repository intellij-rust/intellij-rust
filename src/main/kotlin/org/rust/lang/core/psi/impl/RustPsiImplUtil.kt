package org.rust.lang.core.psi.impl

import org.rust.lang.core.psi.*
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.symbols.RustPathSegment

/**
 * Mixin methods to implement PSI interfaces without copy pasting and
 * introducing monster base classes. Can be simplified when Kotlin supports
 * default methods in interfaces with mixed Kotlin-Java hierarchies (KT-9073 ).
 */
object RustPsiImplUtil {
    fun <PsiT> isPublic(o: PsiT): Boolean
        where PsiT : RustStubbedNamedElementImpl<*>,
              PsiT : RustVisibilityOwner
    {
        return o.stub?.isPublic ?: isPublicNonStubbed(o)
    }

    fun isPublicNonStubbed(element: RustVisibilityOwner): Boolean = element.vis != null

    fun crateRelativePath(element: RustNamedElement): RustPath.CrateRelative? {
        val segment = element.name?.let { RustPathSegment.withoutGenerics(it) } ?: return null
        return element.containingMod.crateRelativePath?.join(segment)
    }

    fun modCrateRelativePath(mod: RustMod): RustPath.CrateRelative? {
        val segments = mod.superMods.asReversed().drop(1).map {
            RustPathSegment.withoutGenerics(it.modName ?: return null)
        }
        return RustPath.CrateRelative(segments)
    }
}

