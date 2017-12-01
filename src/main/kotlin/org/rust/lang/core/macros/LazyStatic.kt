/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.descendantOfTypeStrict
import org.rust.lang.core.psi.ext.elementType

fun expandLazyStatic(call: RsMacroCall): ExpansionResult? {
    val arg = call.macroArgument?.tt ?: return null
    val lazyStaticCall = parseLazyStaticCall(arg) ?: return null
    val text = "${if (lazyStaticCall.pub) "pub " else ""}static ${lazyStaticCall.identifier}: ${lazyStaticCall.type} = &${lazyStaticCall.expr};"
    return RsPsiFactory(call.project)
        .createFile(text)
        .descendantOfTypeStrict<RsConstant>()
}

private data class LazyStaticCall(
    val pub: Boolean,
    val identifier: String,
    val type: String,
    val expr: String
)

private fun parseLazyStaticCall(tt: RsTt): LazyStaticCall? {
    // static ref FOO: Foo = Foo::new();
    val pub = tt.firstToken(RsElementTypes.PUB) != null
    val ident = tt.firstToken(RsElementTypes.IDENTIFIER) ?: return null
    val colon = tt.firstToken(RsElementTypes.COLON) ?: return null
    val eq = tt.firstToken(RsElementTypes.EQ) ?: return null
    val semi = tt.firstToken(RsElementTypes.SEMICOLON) ?: return null

    val typeStart = colon.textRange.endOffset - tt.textRange.startOffset
    val typeEnd = eq.textRange.startOffset - tt.textRange.startOffset
    if (typeStart >= typeEnd) return null
    val typeText = tt.text.substring(typeStart, typeEnd)

    val exprStart = eq.textRange.endOffset - tt.textRange.startOffset
    val exprEnd = semi.textRange.startOffset - tt.textRange.startOffset
    if (exprStart >= exprEnd) return null
    val exprText = tt.text.substring(exprStart, exprEnd)

    return LazyStaticCall(pub, ident.text, typeText, exprText)
}

private fun RsTt.firstToken(type: IElementType): PsiElement? {
    var child = firstChild
    while (child != null) {
        if (child.elementType == type) return child
        child = child.nextSibling
    }
    return null
}
