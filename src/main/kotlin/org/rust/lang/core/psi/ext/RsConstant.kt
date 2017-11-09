/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.macros.ExpansionResult
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.DEFAULT
import org.rust.lang.core.stubs.RsConstantStub
import org.rust.lang.core.types.ty.Mutability

enum class RsConstantKind {
    STATIC,
    MUT_STATIC,
    CONST
}

val RsConstant.kind: RsConstantKind get() = when {
    mut != null -> RsConstantKind.MUT_STATIC
    const != null -> RsConstantKind.CONST
    else -> RsConstantKind.STATIC
}

sealed class RsConstantOwner {
    object Free : RsConstantOwner()
    object Foreign : RsConstantOwner()
    class Trait(val trait: RsTraitItem) : RsConstantOwner()
    class Impl(val impl: RsImplItem) : RsConstantOwner()
}

val RsConstant.owner: RsConstantOwner get() {
    return when (parent) {
        is RsItemsOwner -> RsConstantOwner.Free
        is RsForeignModItem -> RsConstantOwner.Foreign
        is RsMembers -> {
            val grandDad = parent.parent
            when (grandDad) {
                is RsTraitItem -> RsConstantOwner.Trait(grandDad)
                is RsImplItem -> RsConstantOwner.Impl(grandDad)
                else -> error("unreachable")
            }
        }
        else -> error("Unexpected constant parent: $parent")
    }
}

val RsConstant.default: PsiElement?
    get() = node.findChildByType(DEFAULT)?.psi

val RsConstant.mutability: Mutability get() = Mutability.valueOf(mut != null)

abstract class RsConstantImplMixin : RsStubbedNamedElementImpl<RsConstantStub>, RsConstant {
    constructor(node: ASTNode) : super(node)

    constructor(stub: RsConstantStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int) = iconWithVisibility(flags, when (kind) {
        RsConstantKind.CONST -> RsIcons.CONSTANT
        RsConstantKind.MUT_STATIC -> RsIcons.MUT_STATIC
        RsConstantKind.STATIC -> RsIcons.STATIC
    })

    override val isPublic: Boolean get() = RsPsiImplUtil.isPublic(this, stub)

    override val isAbstract: Boolean get() = expr == null

    override fun getContext(): RsElement = ExpansionResult.getContextImpl(this)
}
