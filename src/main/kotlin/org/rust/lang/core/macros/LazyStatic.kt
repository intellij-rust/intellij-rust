/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.elementType

fun expandLazyStatic(call: RsMacroCall): List<RsExpandedElement>? {
    val arg = call.macroArgument?.compactTT ?: return null
    val lazyStaticRefs = parseLazyStaticCall(arg)
    val texts = lazyStaticRefs.map { "${if (it.pub) "pub " else ""}static ${it.identifier}: ${it.type} = &${it.expr};" }
    return RsPsiFactory(call.project)
        .createFile(texts.joinToString("\n"))
        .descendantsOfType<RsConstant>()
        .toList()
}

private data class LazyStaticRef(
    val pub: Boolean,
    val identifier: String,
    val type: String,
    val expr: String
)

private fun parseLazyStaticCall(tt: RsCompactTT): List<LazyStaticRef> {
    val calls = mutableListOf<LazyStaticRef>()

    var start = tt.firstChild
    while (start != null) {
        parseLazyStaticRef(start, tt)?.let { calls.add(it) }
        start = start.firstSibling(RsElementTypes.SEMICOLON)?.nextSibling
    }

    return calls
}

private fun parseLazyStaticRef(start: PsiElement, parent: RsCompactTT): LazyStaticRef? {
    // static ref FOO: Foo = Foo::new();
    val pub = start.firstSibling(RsElementTypes.PUB) != null
    val ident = start.firstSibling(RsElementTypes.IDENTIFIER) ?: return null
    val colon = start.firstSibling(RsElementTypes.COLON) ?: return null
    val eq = start.firstSibling(RsElementTypes.EQ) ?: return null
    val semi = start.firstSibling(RsElementTypes.SEMICOLON) ?: return null

    val typeStart = colon.textRange.endOffset - parent.textRange.startOffset
    val typeEnd = eq.textRange.startOffset - parent.textRange.startOffset
    if (typeStart >= typeEnd) return null
    val typeText = parent.text.substring(typeStart, typeEnd)

    val exprStart = eq.textRange.endOffset - parent.textRange.startOffset
    val exprEnd = semi.textRange.startOffset - parent.textRange.startOffset
    if (exprStart >= exprEnd) return null
    val exprText = parent.text.substring(exprStart, exprEnd)

    return LazyStaticRef(pub, ident.text, typeText, exprText)
}

private fun PsiElement.firstSibling(type: IElementType): PsiElement? {
    var element: PsiElement? = this
    while (element != null) {
        if (element.elementType == type) return element
        element = element.nextSibling
    }
    return null
}
