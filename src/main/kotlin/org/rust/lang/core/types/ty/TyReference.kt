/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.regions.Region

data class TyReference(
    val referenced: Ty,
    val mutability: Mutability,
    val region: Region = ReUnknown
) : Ty(referenced.flags or region.flags) {

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyReference(referenced.foldWith(folder), mutability, region.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        referenced.visitWith(visitor) || region.visitWith(visitor)

    /**
     * We ignore lifetimes when comparing because we don't yet know how to compare them.
     */
    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        javaClass != other?.javaClass -> false
        other !is TyReference -> false
        referenced != other.referenced -> false
        mutability != other.mutability -> false
        else -> true
    }

    override fun hashCode(): Int = 31 * referenced.hashCode() + mutability.hashCode()
}
