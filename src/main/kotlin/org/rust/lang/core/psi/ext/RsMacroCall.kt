/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.CachedValuesManager
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.macros.expandMacro
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.unescapedText
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
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

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)
}

val RsMacroCall.macroName: String
    get() {
        val stub = stub
        if (stub != null) return stub.macroName
        return referenceNameElement.unescapedText
    }

val RsMacroCall.macroBody: String?
    get() {
        val stub = stub
        if (stub != null) return stub.macroBody
        return macroArgument?.compactTT?.text
            ?: formatMacroArgument?.braceListBodyText()?.toString()
            ?: logMacroArgument?.braceListBodyText()?.toString()
    }

val RsMacroCall.expansion: List<RsExpandedElement>?
    get() = CachedValuesManager.getCachedValue(this) {
        expandMacro(this)
    }

fun RsMacroCall.expandAllMacrosRecursively(): String =
    expandAllMacrosRecursively(0)

private fun RsMacroCall.expandAllMacrosRecursively(depth: Int): String {
    if (depth > DEFAULT_RECURSION_LIMIT) return text

    fun toExpandedText(element: PsiElement): String =
        when (element) {
            is RsMacroCall -> element.expandAllMacrosRecursively(depth + 1)
            is RsElement -> element.directChildren.joinToString(" ") { toExpandedText(it) }
            else -> element.text
        }

    return expansion?.joinToString(" ") { toExpandedText(it) } ?: text
}

fun RsMacroCall.processExpansionRecursively(processor: (RsExpandedElement) -> Boolean): Boolean =
    processExpansionRecursively(processor, 0)

private fun RsMacroCall.processExpansionRecursively(processor: (RsExpandedElement) -> Boolean, depth: Int): Boolean {
    if (depth > DEFAULT_RECURSION_LIMIT) return true
    return expansion.orEmpty().any { it.processRecursively(processor, depth) }
}

private fun RsExpandedElement.processRecursively(processor: (RsExpandedElement) -> Boolean, depth: Int): Boolean {
    return when (this) {
        is RsMacroCall -> processExpansionRecursively(processor, depth + 1)
        else -> processor(this)
    }
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

private val PsiElement.directChildren: Sequence<PsiElement>
    get() = generateSequence(firstChild) { it.nextSibling }
