/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiNameIdentifierOwner
import org.rust.lang.core.psi.*

interface RsAbstractable : RsNamedElement, PsiNameIdentifierOwner {
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

val RsAbstractable.owner: RsAbstractableOwner
    get() {
        val stubOnlyParent = when (this) {
            is RsConstant -> if (stub != null) stub.parentStub.psi else parent
            is RsFunction -> if (stub != null) stub.parentStub.psi else parent
            is RsTypeAlias -> if (stub != null) stub.parentStub.psi else parent
            else -> error("unreachable")
        }
        return when (stubOnlyParent) {
            is RsForeignModItem -> RsAbstractableOwner.Foreign
            is RsMembers -> {
                val traitOrImpl = parent.parent
                when (traitOrImpl) {
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
        val superTrait = rustImplItem.traitRef?.resolveToTrait ?: return null
        return when (this) {
            is RsConstant -> superTrait.members?.constantList?.find { it.name == this.name }
            is RsFunction -> superTrait.members?.functionList?.find { it.name == this.name }
            is RsTypeAlias -> superTrait.members?.typeAliasList?.find { it.name == this.name }
            else -> error("unreachable")
        }
    }
