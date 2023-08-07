/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import com.intellij.util.EmptyQuery
import com.intellij.util.Query
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.infer.type
import org.rust.lang.core.types.rawType
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.openapiext.filterQuery
import org.rust.openapiext.mapQuery
import javax.swing.Icon

interface RsAbstractable : RsNameIdentifierOwner, RsExpandedElement, RsVisible, RsDocAndAttributeOwner {
    val isAbstract: Boolean
    fun getIcon(flags: Int, allowNameResolution: Boolean): Icon
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
val RsAbstractable.ownerBySyntaxOnly: RsAbstractableOwner get() = getOwner(PsiElement::stubParent)

private inline fun RsAbstractable.getOwner(getAncestor: PsiElement.() -> PsiElement?): RsAbstractableOwner {
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
        is RsConstant -> traitImpls.mapQuery { impl -> impl.expandedMembers.constants.find { it.name == this.name } }
        is RsFunction -> traitImpls.mapQuery { impl -> impl.expandedMembers.functions.find { it.name == this.name } }
        is RsTypeAlias -> traitImpls.mapQuery { impl -> impl.expandedMembers.types.find { it.name == this.name } }
        else -> EmptyQuery()
    }
    return query.filterQuery { it != null }
}

/**
 * If function or constant is defined in a trait
 * ```rust
 * trait Trait {
 *     fn foo() {}
 * }
 * ```
 * it potentially can be accessed by the trait name `Trait::foo` only if there are `self` parameter or
 * `Self` type in the signature
 */
val RsAbstractable.canBeAccessedByTraitName: Boolean
    get() {
        check(owner is RsAbstractableOwner.Trait)
        val type = when (this) {
            is RsFunction -> {
                if (selfParameter != null) return true
                type
            }
            is RsConstant -> typeReference?.rawType ?: return false
            else -> return false
        }
        return type.visitWith(object : TypeVisitor {
            override fun visitTy(ty: Ty): Boolean =
                if (ty is TyTypeParameter && ty.parameter is TyTypeParameter.Self) true else ty.superVisitWith(this)
        })
    }
