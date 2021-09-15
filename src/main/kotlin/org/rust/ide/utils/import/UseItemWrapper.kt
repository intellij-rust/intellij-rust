/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils.import

import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.RsUseSpeck
import org.rust.lang.core.psi.ext.basePath
import org.rust.lang.core.psi.ext.containingCrate

class UseItemWrapper(val useItem: RsUseItem) : Comparable<UseItemWrapper> {
    private val basePath: RsPath? = useItem.useSpeck?.path?.basePath()

    private val useSpeckText: String? = useItem.useSpeck?.pathTextLower

    // `use` order:
    // 1. Standard library (stdlib)
    // 2. Related third party (extern crate)
    // 3. Local
    //    - otherwise
    //    - crate::
    //    - super::
    //    - self::
    val packageGroupLevel: Int = when {
        basePath?.self != null -> 6
        basePath?.`super` != null -> 5
        basePath?.crate != null -> 4
        else -> when (basePath?.reference?.resolve()?.containingCrate?.origin) {
            PackageOrigin.WORKSPACE -> 3
            PackageOrigin.DEPENDENCY -> 2
            PackageOrigin.STDLIB, PackageOrigin.STDLIB_DEPENDENCY -> 1
            null -> 3
        }
    }

    override fun compareTo(other: UseItemWrapper): Int =
        compareValuesBy(this, other, { it.packageGroupLevel }, { it.useSpeckText })
}

private val RsUseSpeck.pathTextLower: String? get() = path?.text?.toLowerCase()

val COMPARATOR_FOR_SPECKS_IN_USE_GROUP: Comparator<RsUseSpeck> =
    compareBy<RsUseSpeck> { it.path?.self == null }.thenBy { it.pathTextLower }
