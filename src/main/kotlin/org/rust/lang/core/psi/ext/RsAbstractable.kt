/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.*

interface RsAbstractable : RsNameIdentifierOwner, RsExpandedElement {
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
        val rustImplItem = ancestorStrict<RsImplItem>() ?: return null
        val superTrait = rustImplItem.traitRef?.resolveToTrait() ?: return null
        return superTrait.findCorrespondingElement(this)
    }

fun RsTraitOrImpl.findCorrespondingElement(element: RsAbstractable): RsAbstractable? {
    val members = members ?: return null
    return when (element) {
        is RsConstant -> members.constantList.find { it.name == element.name }
        is RsFunction -> members.functionList.find { it.name == element.name }
        is RsTypeAlias -> members.typeAliasList.find { it.name == element.name }
        else -> error("unreachable")
    }
}
