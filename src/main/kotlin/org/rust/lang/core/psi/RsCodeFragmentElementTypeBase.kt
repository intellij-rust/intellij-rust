/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilderFactory
import com.intellij.psi.impl.source.tree.ICodeFragmentElementType
import com.intellij.psi.tree.IElementType
import org.rust.lang.RsLanguage
import org.rust.lang.core.parser.RustParser

abstract class RsCodeFragmentElementTypeBase(debugName: String) : ICodeFragmentElementType(debugName, RsLanguage) {
    protected abstract val elementType: IElementType
    override fun parseContents(chameleon: ASTNode): ASTNode? {
        val project = chameleon.psi.project
        val builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon)
        val root = RustParser().parse(elementType, builder)
        return root.firstChildNode
    }
}

fun factory(name: String): IElementType = when (name) {
    "EXPR_CODE_FRAGMENT" -> RsExprCodeFragmentElementType()
    else -> error("Unknown element $name")
}
