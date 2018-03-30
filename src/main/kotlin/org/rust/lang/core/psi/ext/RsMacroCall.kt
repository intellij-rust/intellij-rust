/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.CachedValuesManager
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

    override fun getContext(): RsElement = ExpansionResult.getContextImpl(this)
}

val RsMacroCall.macroName: String
    get() {
        val stub = stub
        if (stub != null) return stub.macroName
        return referenceNameElement.text
    }

val RsMacroCall.macroBody: String?
    get() {
        val stub = stub
        if (stub != null) return stub.macroBody
        return macroArgument?.compactTT?.text
    }

val RsMacroCall.expansion: List<ExpansionResult>?
    get() = CachedValuesManager.getCachedValue(this) {
        expandMacro(this)
    }

private fun PsiElement.braceListBodyText(): CharSequence? =
    textBetweenParens(firstChild, lastChild)

private fun PsiElement.textBetweenParens(bra: PsiElement?, ket: PsiElement?): CharSequence? {
    if (bra == null || ket == null || bra == ket) return null
    return containingFile.text.subSequence(
        bra.textRange.endOffset,
        ket.textRange.startOffset
    )
}
