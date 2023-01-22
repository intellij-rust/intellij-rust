/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageUtil
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.rust.ide.console.RsConsoleCodeFragmentContext
import org.rust.ide.console.RsConsoleView
import org.rust.lang.RsDebugInjectionListener
import org.rust.lang.core.lexer.RsLexer
import org.rust.lang.core.psi.*
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsPathStub
import org.rust.lang.doc.psi.RsDocCommentElementType
import org.rust.lang.doc.psi.RsDocLinkDestination
import org.rust.lang.doc.psi.ext.isDocCommentLeafToken

class RustParserDefinition : ParserDefinition {

    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        val default = { RsFile(viewProvider) }

        val project = viewProvider.manager.project
        val injectionHost = InjectedLanguageManager.getInstance(project).getInjectionHost(viewProvider)

        if (injectionHost != null) {
            // this class is contained in clion.jar, so it cannot be used inside `is` type check
            if (injectionHost.javaClass.simpleName != "GDBExpressionPlaceholder") {
                return default()
            }

            val injectionListener = project.messageBus.syncPublisher(RsDebugInjectionListener.INJECTION_TOPIC)
            val contextResult = RsDebugInjectionListener.DebugContext()
            injectionListener.evalDebugContext(injectionHost, contextResult)
            val context = contextResult.element ?: return default()

            val fragment = RsDebuggerExpressionCodeFragment(viewProvider, context)
            injectionListener.didInject(injectionHost)

            return fragment
        } else if (viewProvider.virtualFile.name == RsConsoleView.VIRTUAL_FILE_NAME) {
            val context = RsConsoleCodeFragmentContext.createContext(project, null)
            return RsReplCodeFragment(viewProvider, context)
        }
        return default()
    }

    override fun spaceExistenceTypeBetweenTokens(left: ASTNode, right: ASTNode): ParserDefinition.SpaceRequirements {
        val leftElementType = left.elementType
        if (leftElementType == EOL_COMMENT) {
            return ParserDefinition.SpaceRequirements.MUST_LINE_BREAK
        }
        val rightElementType = right.elementType
        if (leftElementType.isDocCommentLeafToken) {
            return when {
                rightElementType.isDocCommentLeafToken -> ParserDefinition.SpaceRequirements.MAY
                /** See [RsDocLinkDestination] */
                right.treeParent?.elementType == RsPathStub.Type -> ParserDefinition.SpaceRequirements.MAY
                else -> ParserDefinition.SpaceRequirements.MUST_LINE_BREAK
            }
        }
        if (rightElementType.isDocCommentLeafToken && left.treeParent?.elementType == RsPathStub.Type) {
            return ParserDefinition.SpaceRequirements.MAY
        }
        return LanguageUtil.canStickTokensTogetherByLexer(left, right, RsLexer())
    }

    override fun getFileNodeType(): IFileElementType = RsFileStub.Type

    override fun getStringLiteralElements(): TokenSet = RS_ALL_STRING_LITERALS

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
        @JvmField val INNER_BLOCK_DOC_COMMENT = RsDocCommentElementType("<INNER_BLOCK_DOC_COMMENT>")
        @JvmField val OUTER_BLOCK_DOC_COMMENT = RsDocCommentElementType("<OUTER_BLOCK_DOC_COMMENT>")
        @JvmField val INNER_EOL_DOC_COMMENT = RsDocCommentElementType("<INNER_EOL_DOC_COMMENT>")
        @JvmField val OUTER_EOL_DOC_COMMENT = RsDocCommentElementType("<OUTER_EOL_DOC_COMMENT>")

        /**
         * Should be increased after any change of lexer rules
         */
        const val LEXER_VERSION: Int = 4

        /**
         * Should be increased after any change of parser rules
         */
        const val PARSER_VERSION: Int = LEXER_VERSION + 45
    }
}
