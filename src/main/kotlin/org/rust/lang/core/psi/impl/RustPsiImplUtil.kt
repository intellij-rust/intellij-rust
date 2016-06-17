package org.rust.lang.core.psi.impl

import org.rust.lang.core.psi.RustVisibilityOwner

object RustPsiImplUtil {
    /**
     * Mixin method to implement [RustVisibilityOwner] without copy pasting and
     * introducing monster base classes. Can be simplified when Kotlin supports
     * default methods in interfaces with mixed Kotlin-Java hierarchies.
     */
    fun <PsiT> isPublic(o: PsiT): Boolean
        where PsiT: RustStubbedNamedElementImpl<*>,
              PsiT: RustVisibilityOwner
    {
        return o.stub?.isPublic ?: o.vis != null
    }
}

