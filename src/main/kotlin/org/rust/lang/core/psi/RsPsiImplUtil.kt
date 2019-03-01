/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.superMods

/**
 * Mixin methods to implement PSI interfaces without copy pasting and
 * introducing monster base classes. Can be simplified when Kotlin supports
 * default methods in interfaces with mixed Kotlin-Java hierarchies (KT-9073 ).
 */
object RsPsiImplUtil {
    fun crateRelativePath(element: RsNamedElement): String? {
        val name = element.name ?: return null
        val qualifier = element.containingMod.crateRelativePath ?: return null
        return "$qualifier::$name"
    }

    fun modCrateRelativePath(mod: RsMod): String? {
        val segments = mod.superMods.asReversed().drop(1).map {
            it.modName ?: return null
        }
        if (segments.isEmpty()) return ""
        return "::" + segments.joinToString("::")
    }
}

