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

fun factory(name: String): IElementType = when (name) {
    "EXPR_CODE_FRAGMENT" -> RsExprCodeFragmentElementType
    "STMT_CODE_FRAGMENT" -> RsStmtCodeFragmentElementType
    "TYPE_REF_CODE_FRAGMENT" -> RsTypeRefCodeFragmentElementType
    else -> error("Unknown element $name")
}

abstract class RsCodeFragmentElementTypeBase(debugName: String) : ICodeFragmentElementType(debugName, RsLanguage) {
    override fun parseContents(chameleon: ASTNode): ASTNode? {
        val project = chameleon.psi.project
        val builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon)
        val root = RustParser().parse(this, builder)
        return root.firstChildNode
    }
}

object RsExprCodeFragmentElementType : RsCodeFragmentElementTypeBase("RS_EXPR_CODE_FRAGMENT")
object RsStmtCodeFragmentElementType : RsCodeFragmentElementTypeBase("RS_STMT_CODE_FRAGMENT")
object RsTypeRefCodeFragmentElementType : RsCodeFragmentElementTypeBase("RS_TYPE_REF_CODE_FRAGMENT")
