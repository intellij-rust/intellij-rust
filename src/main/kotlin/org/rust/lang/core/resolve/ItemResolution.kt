/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.skipParens
import org.rust.lang.core.psi.ext.variants
import org.rust.lang.core.resolve.ref.advancedDeepResolve
import org.rust.lang.core.resolve2.processItemDeclarations

fun processItemOrEnumVariantDeclarations(
    scope: RsElement,
    ns: Set<Namespace>,
    processor: RsResolveProcessor,
    withPrivateImports: () -> Boolean
): Boolean {
    when (scope) {
        // https://github.com/rust-lang/rfcs/blob/master/text/2338-type-alias-enum-variants.md
        is RsTypeAlias -> {
            val (item, subst) = (scope.typeReference?.skipParens() as? RsBaseType)
                ?.path?.reference?.advancedDeepResolve() ?: return false
            if (item is RsEnumItem) {
                if (processAllWithSubst(item.variants, subst, processor)) return true
            }
        }
        is RsImplItem -> {
            val (item, subst) = (scope.typeReference?.skipParens() as? RsBaseType)
                ?.path?.reference?.advancedDeepResolve() ?: return false
            if (item is RsEnumItem) {
                if (processAllWithSubst(item.variants, subst, processor)) return true
            }
        }
        is RsEnumItem -> {
            if (processAll(scope.variants, processor)) return true
        }
        is RsMod -> {
            val ipm = if (withPrivateImports()) {
                ItemProcessingMode.WITH_PRIVATE_IMPORTS
            } else {
                ItemProcessingMode.WITHOUT_PRIVATE_IMPORTS
            }
            if (processItemDeclarations(scope, ns, processor, ipm)) return true
        }
    }

    return false
}

enum class ItemProcessingMode(val withExternCrates: Boolean) {
    WITHOUT_PRIVATE_IMPORTS(false),
    WITH_PRIVATE_IMPORTS(false),
    WITH_PRIVATE_IMPORTS_N_EXTERN_CRATES(true),
}
