/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageUtil
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.lexer.RsLexer
import org.rust.lang.core.psi.RS_COMMENTS
import org.rust.lang.core.psi.RS_EOL_COMMENTS
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsElementTypes.STRING_LITERAL
import org.rust.lang.core.psi.RsTokenType
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.stubs.RsFileStub

class RustParserDefinition : ParserDefinition {

    override fun createFile(viewProvider: FileViewProvider): PsiFile? =
        RsFile(viewProvider)

    override fun spaceExistanceTypeBetweenTokens(left: ASTNode, right: ASTNode): ParserDefinition.SpaceRequirements {
        if (left.elementType in RS_EOL_COMMENTS) return ParserDefinition.SpaceRequirements.MUST_LINE_BREAK
        return LanguageUtil.canStickTokensTogetherByLexer(left, right, RsLexer())
    }

    override fun getFileNodeType(): IFileElementType = RsFileStub.Type

    override fun getStringLiteralElements(): TokenSet =
        TokenSet.create(STRING_LITERAL)

    override fun getWhitespaceTokens(): TokenSet =
        TokenSet.create(TokenType.WHITE_SPACE)

    override fun getCommentTokens() = RS_COMMENTS

    override fun createElement(node: ASTNode?): PsiElement =
        RsElementTypes.Factory.createElement(node)

    override fun createLexer(project: Project?): Lexer = RsLexer()

    override fun createParser(project: Project?): PsiParser = RustParser()

    companion object {
        @JvmField val BLOCK_COMMENT = RsTokenType("<BLOCK_COMMENT>")
        @JvmField val EOL_COMMENT = RsTokenType("<EOL_COMMENT>")
        @JvmField val INNER_BLOCK_DOC_COMMENT = RsTokenType("<INNER_BLOCK_DOC_COMMENT>")
        @JvmField val OUTER_BLOCK_DOC_COMMENT = RsTokenType("<OUTER_BLOCK_DOC_COMMENT>")
        @JvmField val INNER_EOL_DOC_COMMENT = RsTokenType("<INNER_EOL_DOC_COMMENT>")
        @JvmField val OUTER_EOL_DOC_COMMENT = RsTokenType("<OUTER_EOL_DOC_COMMENT>")
    }
}
