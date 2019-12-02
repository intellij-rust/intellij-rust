/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilderFactory
import com.intellij.psi.impl.source.tree.ICodeFragmentElementType
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.RsLanguage
import org.rust.lang.core.parser.RustParser

class RsCodeFragmentElementType(private val elementType: IElementType, debugName: String) : ICodeFragmentElementType(debugName, RsLanguage) {

    override fun parseContents(chameleon: ASTNode): ASTNode? {
        if (chameleon !is TreeElement) return null
        val project = chameleon.manager.project
        val builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon)
        val root = RustParser().parse(elementType, builder)
        return root.firstChildNode
    }

    companion object {
        val EXPR = RsCodeFragmentElementType(RsElementTypes.EXPR_CODE_FRAGMENT, "RS_EXPR_CODE_FRAGMENT")
        val STMT = RsCodeFragmentElementType(RsElementTypes.STMT_CODE_FRAGMENT, "RS_STMT_CODE_FRAGMENT")
        val REPL = RsCodeFragmentElementType(RsElementTypes.REPL_CODE_FRAGMENT, "RS_REPL_CODE_FRAGMENT")
        val TYPE_REF = RsCodeFragmentElementType(RsElementTypes.TYPE_REF_CODE_FRAGMENT, "RS_TYPE_REF_CODE_FRAGMENT")
        val TYPE_PATH_CODE_FRAGMENT = RsCodeFragmentElementType(RsElementTypes.TYPE_PATH_CODE_FRAGMENT, "RS_TYPE_PATH_CODE_FRAGMENT")
        val VALUE_PATH_CODE_FRAGMENT = RsCodeFragmentElementType(RsElementTypes.VALUE_PATH_CODE_FRAGMENT, "RS_VALUE_PATH_CODE_FRAGMENT")
    }
}
