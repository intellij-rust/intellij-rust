/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.CachedValueImpl
import org.rust.ide.icons.RsIcons
import org.rust.ide.presentation.getPresentation
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsElementTypes.DEFAULT
import org.rust.lang.core.psi.RsElementTypes.EXCL
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.rustStructureOrAnyPsiModificationTracker
import org.rust.lang.core.resolve.RsCachedImplItem
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.stubs.RsImplItemStub
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.RsPsiTypeImplUtil
import org.rust.lang.core.types.normType
import org.rust.lang.core.types.ty.*

val RsImplItem.default: PsiElement?
    get() = node.findChildByType(DEFAULT)?.psi

/** `impl !Sync for Bar` vs `impl Foo for Bar` */
val RsImplItem.isNegativeImpl: Boolean
    get() = greenStub?.isNegativeImpl ?: (node.findChildByType(EXCL) != null)

val RsImplItem.isReservationImpl: Boolean
    get() = IMPL_ITEM_IS_RESERVATION_IMPL_PROP.getByPsi(this)

val IMPL_ITEM_IS_RESERVATION_IMPL_PROP: StubbedAttributeProperty<RsImplItem, RsImplItemStub> =
    StubbedAttributeProperty({ it.hasAttribute("rustc_reservation_impl") }, RsImplItemStub::mayBeReservationImpl)

val RsImplItem.implementingType: TyAdt?
    get() = typeReference?.normType as? TyAdt

abstract class RsImplItemImplMixin : RsStubbedElementImpl<RsImplItemStub>, RsImplItem {

    constructor(node: ASTNode) : super(node)
    constructor(stub: RsImplItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int) = RsIcons.IMPL

    override val isPublic: Boolean get() = false // pub does not affect impls at all

    override fun getPresentation(): ItemPresentation = getPresentation(this)

    override fun getTextOffset(): Int = typeReference?.textOffset ?: impl.textOffset

    override val implementedTrait: BoundElement<RsTraitItem>? get() {
        val (trait, subst) = traitRef?.resolveToBoundTrait() ?: return null
        return BoundElement(trait, subst)
    }

    override val associatedTypesTransitively: Collection<RsTypeAlias>
        get() = CachedValuesManager.getCachedValue(this) {
            CachedValueProvider.Result.create(
                doGetAssociatedTypesTransitively(),
                rustStructureOrAnyPsiModificationTracker
            )
        }

    private fun doGetAssociatedTypesTransitively(): List<RsTypeAlias> {
        val implAliases = expandedMembers.types
        val traitAliases = implementedTrait?.associatedTypesTransitively ?: emptyList()
        return implAliases + traitAliases.filter { trAl -> implAliases.find { it.name == trAl.name } == null }
    }

    override val declaredType: Ty get() = RsPsiTypeImplUtil.declaredType(this)

    override val isUnsafe: Boolean get() = unsafe != null

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)

    val cachedImplItem: CachedValue<RsCachedImplItem> = CachedValueImpl {
        val cachedImpl = RsCachedImplItem(this)
        RsCachedImplItem.toCachedResult(this, containingCrate, cachedImpl)
    }
}

// https://doc.rust-lang.org/reference/items/implementations.html#orphan-rules
fun checkOrphanRules(impl: RsImplItem, isSameCrate: (RsElement) -> Boolean): Boolean {
    val traitRef = impl.traitRef ?: return true
    val (trait, subst, _) = traitRef.resolveToBoundTrait() ?: return true
    if (isSameCrate(trait)) return true
    val typeParameters = subst.typeSubst.values + (impl.typeReference?.normType ?: return true)
    return typeParameters.any { tyWrapped ->
        val ty = tyWrapped.unwrapFundamentalTypes()
        ty is TyUnknown
            // `impl ForeignTrait<LocalStruct> for ForeignStruct`
            || ty is TyAdt && isSameCrate(ty.item)
            // `impl ForeignTrait for Box<dyn LocalTrait>`
            || ty is TyTraitObject && ty.baseTrait.let { it == null || isSameCrate(it) }
            // `impl<T> ForeignTrait for Box<T>` in stdlib
            || tyWrapped is TyAdt && isSameCrate(tyWrapped.item)
    }
    // TODO uncovering
}

// https://doc.rust-lang.org/reference/glossary.html#fundamental-type-constructors
private fun Ty.unwrapFundamentalTypes(): Ty {
    when (this) {
        // &T -> T
        // &mut T -> T
        is TyReference -> return referenced
        // Box<T> -> T
        // Pin<T> -> T
        is TyAdt -> {
            if (item == item.knownItems.Box || item == item.knownItems.Pin) {
                return typeArguments.firstOrNull() ?: this
            }
        }
    }
    return this
}
