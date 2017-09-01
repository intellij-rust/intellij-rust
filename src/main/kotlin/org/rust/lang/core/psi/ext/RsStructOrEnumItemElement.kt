/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Query
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.utils.filterIsInstanceQuery
import org.rust.lang.utils.mapQuery

interface RsStructOrEnumItemElement : RsQualifiedNamedElement, RsTypeBearingItemElement, RsGenericDeclaration, RsTypeDeclarationElement

val RsStructOrEnumItemElement.derivedTraits: List<RsTraitItem>
    get() = queryAttributes
        .deriveAttribute
        ?.metaItemArgs
        ?.metaItemList
        ?.mapNotNull { it.reference.resolve() as? RsTraitItem }
        ?: emptyList()


fun RsStructOrEnumItemElement.searchForImplementations(): Query<RsImplItem> {
    return ReferencesSearch.search(this, this.useScope)
        .mapQuery { ref ->
            PsiTreeUtil.getTopmostParentOfType(ref.element, RsTypeReference::class.java)?.parent
        }
        .filterIsInstanceQuery<RsImplItem>()
}
