/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.icons.RsIcons
import org.rust.ide.presentation.getPresentation
import org.rust.lang.core.psi.*
import org.rust.lang.core.resolve.ref.RsPatBindingReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.types.ty.Mutability

sealed class RsBindingModeKind {
    class BindByReference(val mutability: Mutability) : RsBindingModeKind()
    class BindByValue(val mutability: Mutability) : RsBindingModeKind()
}

val RsPatBinding.mutability: Mutability
    get() {
        return when (val kind = kind) {
            is RsBindingModeKind.BindByValue -> kind.mutability
            is RsBindingModeKind.BindByReference -> Mutability.IMMUTABLE
        }
    }

val RsPatBinding.isArg: Boolean get() = parent?.parent is RsValueParameter

val RsPatBinding.kind: RsBindingModeKind
    get() {
        val bindingMode = bindingMode
        val ref = bindingMode?.ref != null
        val mutability = Mutability.valueOf(bindingMode?.mut != null)

        return if (ref) RsBindingModeKind.BindByReference(mutability) else RsBindingModeKind.BindByValue(mutability)
    }

val RsPatBinding.topLevelPattern: RsPat
    get() = ancestors
        .dropWhile { it is RsPat || it is RsPatField }
        .filterIsInstance<RsPat>()
        .lastOrNull()
        ?: error("Binding outside the pattern: `${this.text}`")

val RsPatBinding.isReferenceToConstant: Boolean get() = reference.resolve() != null

abstract class RsPatBindingImplMixin(node: ASTNode) : RsNamedElementImpl(node),
                                                      RsPatBinding {

    // XXX: RsPatBinding is both a name element and a reference element:
    //
    // ```
    // match Some(82) {
    //     None => { /* None is a reference */ }
    //     Nope => { /* Nope is a named element*/ }
    // }
    // ```
    override fun getReference(): RsReference = RsPatBindingReferenceImpl(this)

    override val referenceNameElement: PsiElement get() = nameIdentifier!!
    override val referenceName: String get() = name!!

    override fun getIcon(flags: Int) = when {
        isArg && mutability.isMut -> RsIcons.MUT_ARGUMENT
        isArg -> RsIcons.ARGUMENT
        mutability.isMut -> RsIcons.MUT_BINDING
        else -> RsIcons.BINDING
    }

    override fun getUseScope(): SearchScope {
        val owner = PsiTreeUtil.getParentOfType(this,
            RsBlock::class.java,
            RsFunction::class.java,
            RsLambdaExpr::class.java
        )

        if (owner != null) return RsPsiImplUtil.localOrMacroSearchScope(owner)

        return super.getUseScope()
    }

    override fun getPresentation(): ItemPresentation = getPresentation(this)
}
