/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.rust.lang.core.macros.MacroExpansion
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.rustStructureOrAnyPsiModificationTracker
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import org.rust.lang.core.stubs.RsMacroCallStub
import org.rust.stdext.HashCode


abstract class RsMacroCallImplMixin : RsStubbedElementImpl<RsMacroCallStub>,
                                      RsMacroCall {

    constructor(node: ASTNode) : super(node)
    constructor(stub: RsMacroCallStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)

    override val modificationTracker: SimpleModificationTracker =
        SimpleModificationTracker()

    override fun incModificationCount(element: PsiElement): Boolean {
        modificationTracker.incModificationCount()
        return false // force rustStructureModificationTracker to be incremented
    }
}

val RsMacroCall.macroName: String
    get() = path.referenceName

val RsMacroCall.macroBody: String?
    get() {
        val stub = stub
        if (stub != null) return stub.macroBody
        return macroArgument?.compactTT?.text
            ?: formatMacroArgument?.braceListBodyText()?.toString()
            ?: logMacroArgument?.braceListBodyText()?.toString()
            ?: assertMacroArgument?.braceListBodyText()?.toString()
            ?: exprMacroArgument?.braceListBodyText()?.toString()
            ?: vecMacroArgument?.braceListBodyText()?.toString()
    }

val RsMacroCall.bodyHash: HashCode?
    get() = CachedValuesManager.getCachedValue(this) {
        val body = macroBody
        val hash = body?.let { HashCode.compute(it) }
        CachedValueProvider.Result.create(hash, modificationTracker)
    }

fun RsMacroCall.resolveToMacro(): RsMacro? =
    path.reference.resolve() as? RsMacro

val RsMacroCall.expansion: MacroExpansion?
    get() = CachedValuesManager.getCachedValue(this) {
        val project = project
        CachedValueProvider.Result.create(
            project.macroExpansionManager.getExpansionFor(CompletionUtil.getOriginalOrSelf(this)),
            rustStructureOrAnyPsiModificationTracker
        )
    }

fun RsMacroCall.expandAllMacrosRecursively(): String =
    expandAllMacrosRecursively(0)

private fun RsMacroCall.expandAllMacrosRecursively(depth: Int): String {
    if (depth > DEFAULT_RECURSION_LIMIT) return text

    fun toExpandedText(element: PsiElement): String =
        when (element) {
            is RsMacroCall -> element.expandAllMacrosRecursively(depth)
            is RsElement -> element.directChildren.joinToString(" ") { toExpandedText(it) }
            else -> element.text
        }

    return expansion?.elements?.joinToString(" ") { toExpandedText(it) } ?: text
}

fun RsMacroCall.processExpansionRecursively(processor: (RsExpandedElement) -> Boolean): Boolean =
    processExpansionRecursively(processor, 0)

private fun RsMacroCall.processExpansionRecursively(processor: (RsExpandedElement) -> Boolean, depth: Int): Boolean {
    if (depth > DEFAULT_RECURSION_LIMIT) return true
    return expansion?.elements.orEmpty().any { it.processRecursively(processor, depth) }
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
        bra.endOffset,
        ket.startOffset
    )
}

private val PsiElement.directChildren: Sequence<PsiElement>
    get() = generateSequence(firstChild) { it.nextSibling }
