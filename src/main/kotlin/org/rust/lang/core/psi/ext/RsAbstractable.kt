/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.intellij.util.EmptyQuery
import com.intellij.util.Query
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.*
import org.rust.openapiext.filterQuery
import org.rust.openapiext.mapQuery

interface RsAbstractable : RsNameIdentifierOwner, RsExpandedElement, RsVisible {
    val isAbstract: Boolean
}

sealed class RsAbstractableOwner {
    object Free : RsAbstractableOwner()
    object Foreign : RsAbstractableOwner()
    class Trait(val trait: RsTraitItem) : RsAbstractableOwner()
    class Impl(val impl: RsImplItem, val isInherent: Boolean) : RsAbstractableOwner()

    val isInherentImpl: Boolean get() = this is Impl && isInherent
    val isTraitImpl: Boolean get() = this is Impl && !isInherent
    val isImplOrTrait: Boolean get() = this is Impl || this is Trait
}

val RsAbstractable.owner: RsAbstractableOwner get() = getOwner(PsiElement::getContext)

inline fun RsAbstractable.getOwner(getAncestor: PsiElement.() -> PsiElement?): RsAbstractableOwner {
    return when (val ancestor = getAncestor()) {
        is RsForeignModItem -> RsAbstractableOwner.Foreign
        is RsMembers -> {
            when (val traitOrImpl = ancestor.getAncestor()) {
                is RsImplItem -> RsAbstractableOwner.Impl(traitOrImpl, isInherent = traitOrImpl.traitRef == null)
                is RsTraitItem -> RsAbstractableOwner.Trait(traitOrImpl)
                else -> error("unreachable")
            }
        }
        else -> RsAbstractableOwner.Free
    }
}

// Resolve a const, fn or type in a impl block to the corresponding item in the trait block
val RsAbstractable.superItem: RsAbstractable?
    get() {
        val impl = (owner as? RsAbstractableOwner.Impl)?.impl ?: return null
        val superTrait = impl.traitRef?.resolveToTrait() ?: return null
        return superTrait.findCorrespondingElement(this)
    }

fun RsTraitOrImpl.findCorrespondingElement(element: RsAbstractable): RsAbstractable? {
    val members = expandedMembers
    return when (element) {
        is RsConstant -> members.constants.find { it.name == element.name }
        is RsFunction -> members.functions.find { it.name == element.name }
        is RsTypeAlias -> members.types.find { it.name == element.name }
        else -> error("unreachable")
    }
}

fun RsAbstractable.searchForImplementations(): Query<RsAbstractable> {
    val traitItem = ancestorStrict<RsTraitItem>() ?: return EmptyQuery()
    val traitImpls = traitItem.searchForImplementations()

    val query: Query<RsAbstractable> = when (this) {
        is RsConstant -> traitImpls.mapQuery { it.expandedMembers.constants.find { it.name == this.name } }
        is RsFunction -> traitImpls.mapQuery { it.expandedMembers.functions.find { it.name == this.name } }
        is RsTypeAlias -> traitImpls.mapQuery { it.expandedMembers.types.find { it.name == this.name } }
        else -> EmptyQuery()
    }
    return query.filterQuery(Condition { it != null })
}
