/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.lang.core.macros.expandMacro
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.resolve.ref.RsMacroCallReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference


abstract class RsMacroCallImplMixin(node: ASTNode) : RsCompositeElementImpl(node), RsMacroCall {

    override fun getReference(): RsReference = RsMacroCallReferenceImpl(this)

    override val referenceName: String
        get() = referenceNameElement.text

    override val referenceNameElement: PsiElement
        get() = findChildByType(IDENTIFIER)!!

}

val RsMacroCall.macroName: PsiElement? get() = referenceNameElement

val RsMacroCall.expansion: PsiElement?
    get() = CachedValuesManager.getCachedValue(this, {
        CachedValueProvider.Result.create(expandMacro(this), PsiModificationTracker.MODIFICATION_COUNT)
    })
