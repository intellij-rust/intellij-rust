/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.lang.core.macros.ExpansionResult
import org.rust.lang.core.macros.expandMacro
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.resolve.ref.RsMacroCallReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.stubs.RsMacroCallStub


abstract class RsMacroCallImplMixin : RsStubbedElementImpl<RsMacroCallStub>, RsMacroCall {

    constructor(node: ASTNode) : super(node)
    constructor(stub: RsMacroCallStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReference(): RsReference = RsMacroCallReferenceImpl(this)

    override val referenceName: String
        get() = macroName

    override val referenceNameElement: PsiElement
        get() = findChildByType(IDENTIFIER)!!

}

val RsMacroCall.macroName: String
    get() {
        val stub = stub
        if (stub != null) return stub.macroName
        return referenceNameElement.text
    }

val RsMacroCall.expansion: List<ExpansionResult>?
    get() = CachedValuesManager.getCachedValue(this) {
        CachedValueProvider.Result.create(expandMacro(this), PsiModificationTracker.MODIFICATION_COUNT)
    }
