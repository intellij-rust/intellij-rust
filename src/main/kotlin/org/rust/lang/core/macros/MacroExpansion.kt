/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.macroName

fun expandMacro(call: RsMacroCall): ExpansionResult? {
    if (call.macroName != "lazy_static") return null
    val arg = call.macroArgument?.tt ?: return null
    val ctx = call.context as? RsElement ?: return null

    val lazyStaticCall = parseLazyStaticCall(arg) ?: return null
    return RsCodeFragmentFactory(ctx.project)
        .createExpandedItem<RsConstant>(
            "static ${lazyStaticCall.identifier}: ${lazyStaticCall.type} = &${lazyStaticCall.expr};",
            ctx
        )
}

private data class LazyStaticCall(
    val identifier: String,
    val type: String,
    val expr: String
)

private fun parseLazyStaticCall(tt: RsTt): LazyStaticCall? {
    // static ref FOO: Foo = Foo::new();
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

    return LazyStaticCall(ident.text, typeText, exprText)
}


private fun RsTt.firstToken(type: IElementType): PsiElement? {
    var child = firstChild
    while (child != null) {
        if (child.elementType == type) return child
        child = child.nextSibling
    }
    return null
}
