/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.rust.lang.core.crate.Crate
import org.rust.lang.core.macros.proc.ProcMacroApplicationService
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.attributeStub
import org.rust.lang.core.psi.ext.getQueryAttributes
import org.rust.lang.core.psi.ext.name
import org.rust.lang.core.resolve2.resolveToProcMacroWithoutPsiWOHC
import org.rust.lang.core.stubs.RsAttributeOwnerStub
import org.rust.lang.core.stubs.common.RsAttributeOwnerPsiOrStub
import org.rust.lang.core.stubs.common.RsMetaItemPsiOrStub

sealed class ProcMacroAttribute<out T : RsMetaItemPsiOrStub> {
    object None: ProcMacroAttribute<Nothing>()
    object Derive: ProcMacroAttribute<Nothing>()
    data class Attr<T : RsMetaItemPsiOrStub>(val attr: T, val index: Int): ProcMacroAttribute<T>()

    companion object {
        /**
         *
         * Can't be after derive:
         *
         * ```
         * #[derive(Foo)]
         * #[foo] // NOT an attribute macro
         * struct S
         * ```
         */
        fun <T : RsMetaItemPsiOrStub> getProcMacroAttributeRaw(
            owner: RsAttributeOwnerPsiOrStub<T>,
            stub: RsAttributeOwnerStub? = if (owner is RsDocAndAttributeOwner) owner.attributeStub else owner as RsAttributeOwnerStub,
            explicitCrate: Crate? = null,
            explicitCustomAttributes: CustomAttributes? = null,
        ): ProcMacroAttribute<T> {
            if (!RsProcMacroPsiUtil.psiOrStubTypeCanHaveProcMacroCall(owner)) return None
            if (!ProcMacroApplicationService.isEnabled()) return None
            if (stub != null) {
                if (!stub.mayHaveCustomAttrs) {
                    return if (stub.mayHaveCustomDerive) Derive else None
                }
            }

            val crate = explicitCrate ?: owner.containingCrate ?: return None
            val customAttributes = explicitCustomAttributes ?: CustomAttributes.fromCrate(crate)

            owner.getQueryAttributes(crate, stub, fromOuterAttrsOnly = true).metaItems.forEachIndexed { index, meta ->
                if (meta.name == "derive") return Derive
                if (RsProcMacroPsiUtil.canBeProcMacroAttributeCallWithoutContextCheck(meta, customAttributes)) {
                    return Attr(meta, index)
                }
            }
            return None
        }

        fun getProcMacroAttribute(
            owner: RsDocAndAttributeOwner,
            stub: RsAttributeOwnerStub? = owner.attributeStub,
            explicitCrate: Crate? = null,
        ): ProcMacroAttribute<RsMetaItem> {
            return when (val attr = getProcMacroAttributeRaw(owner, stub, explicitCrate)) {
                Derive -> Derive
                None -> None
                is Attr -> attr.takeIf { attr.attr.resolveToProcMacroWithoutPsiWOHC(check = false)?.isHardcodedNotAMacro != true } ?: None
            }
        }
    }
}
