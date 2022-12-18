/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsPathType
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.skipParens
import org.rust.lang.core.psi.ext.variants
import org.rust.lang.core.resolve.ref.advancedDeepResolve
import org.rust.lang.core.resolve2.processItemDeclarations
import org.rust.stdext.BREAK
import org.rust.stdext.CONTINUE
import org.rust.stdext.ShouldStop
import org.rust.stdext.mapBreak

fun processItemOrEnumVariantDeclarations(
    scope: RsElement,
    ns: Set<Namespace>,
    processor: RsResolveProcessor,
    withPrivateImports: () -> Boolean
): ShouldStop {
    when (scope) {
        // https://github.com/rust-lang/rfcs/blob/master/text/2338-type-alias-enum-variants.md
        is RsTypeAlias -> {
            val (item, subst) = (scope.typeReference?.skipParens() as? RsPathType)
                ?.path?.reference?.advancedDeepResolve() ?: return CONTINUE
            return if (item is RsEnumItem) {
                processAllWithSubst(item.variants, subst, processor)
            } else {
                CONTINUE
            }
        }

        is RsImplItem -> {
            val (item, subst) = (scope.typeReference?.skipParens() as? RsPathType)
                ?.path?.reference?.advancedDeepResolve() ?: return CONTINUE
            return if (item is RsEnumItem) {
                processAllWithSubst(item.variants, subst, processor)
            } else {
                CONTINUE
            }
        }

        is RsEnumItem -> {
            return processAll(scope.variants, processor)
        }

        is RsMod -> {
            val ipm = if (withPrivateImports()) {
                ItemProcessingMode.WITH_PRIVATE_IMPORTS
            } else {
                ItemProcessingMode.WITHOUT_PRIVATE_IMPORTS
            }
            return processItemDeclarations(scope, ns, processor, ipm)
        }

        else -> return CONTINUE
    }
}

enum class ItemProcessingMode(val withExternCrates: Boolean) {
    WITHOUT_PRIVATE_IMPORTS(false),
    WITH_PRIVATE_IMPORTS(false),
    WITH_PRIVATE_IMPORTS_N_EXTERN_CRATES(true),
}
