/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.module.Module
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.lang.utils.findWithCache
import java.util.*

class StdKnownItems private constructor(private val absolutePathResolver: (String, String) -> RsNamedElement?) {
    private val binOps by lazy {
        ArithmeticOp.values()
            .map { findCoreItem("ops::${it.traitName}") }
            .mapNotNull { it as? RsTraitItem }
    }

    fun findStdItem(prefixNoStd: String, name: String): RsNamedElement? =
        absolutePathResolver(prefixNoStd, name)

    fun findStdTy(prefixNoStd: String, name: String): Ty {
        val element = findStdItem(prefixNoStd, name) ?: return TyUnknown
        return (element as? RsTypeBearingItemElement)?.type ?: TyUnknown
    }

    fun findCoreItem(name: String): RsNamedElement? =
        findStdItem("core", name)

    fun findCoreTy(name: String): Ty =
        findStdTy("core", name)

    fun findIteratorTrait(): RsTraitItem? =
        findCoreItem("iter::Iterator") as? RsTraitItem

    fun findBinOpTraits(): List<RsTraitItem> = binOps

    companion object {
        fun relativeTo(psi: RsCompositeElement): StdKnownItems {
            data class AbsolutePath(val module: Module, val fullName: String)
            val project = psi.project
            val module = psi.module ?: return StdKnownItems { _, _ -> null }
            val crateRoot = psi.crateRoot as? RsFile ?: return StdKnownItems { _, _ -> null }
            val useStdPrefix = crateRoot.attributes == RsFile.Attributes.NONE

            return StdKnownItems { prefixNoStd, name ->
                val prefix = if (useStdPrefix) "std" else prefixNoStd
                val path = AbsolutePath(module, "$prefix::$name")
                findWithCache(project, path) { _, path ->
                    Optional.ofNullable(resolveStringPath(path.fullName, module)?.first)
                }.orElse(null)
            }
        }
    }
}
