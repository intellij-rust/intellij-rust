/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsPatFieldFull
import org.rust.lang.core.psi.RsPatStruct
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processStructPatternFieldResolveVariants
import org.rust.lang.core.resolve.ref.ResolveCacheDependency
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.resolve.ref.RsReferenceCached


val RsPatFieldFull.parentStructPattern: RsPatStruct
    get() = ancestorStrict()!!


abstract class RsPatFieldFullImplMixin(node: ASTNode) : RsElementImpl(node), RsPatFieldFull {
    override val referenceNameElement: PsiElement
        get() = identifier ?: integerLiteral!!

    override fun getReference(): RsReference = object : RsReferenceCached<RsPatFieldFull>(this@RsPatFieldFullImplMixin) {
        override fun resolveInner(): List<RsElement> =
            collectResolveVariants(element.referenceName) { processStructPatternFieldResolveVariants(element, it) }

        override val cacheDependency: ResolveCacheDependency
            get() = ResolveCacheDependency.LOCAL_AND_RUST_STRUCTURE
    }
}
