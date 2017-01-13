package org.rust.lang.core.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.DefaultASTFactory
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ILeafElementType
import com.intellij.psi.tree.TokenSet
import org.rust.ide.utils.service
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsTokenElementTypes.*

private val defaultASTFactory by lazy { service<DefaultASTFactory>() }

open class RsTokenType(debugName: String) : IElementType(debugName, RsLanguage), ILeafElementType {
    override fun createLeafNode(leafText: CharSequence): ASTNode = LeafPsiElement(this, leafText)
}

class RsCommentTokenType(debugName: String) : RsTokenType(debugName) {
    override fun createLeafNode(leafText: CharSequence): ASTNode = defaultASTFactory.createComment(this, leafText)
}

class RsLiteralTokenType(
    debugName: String,
    private val implConstructor: (IElementType, CharSequence) -> RsLiteral
) : RsTokenType(debugName) {
    override fun createLeafNode(leafText: CharSequence): ASTNode = implConstructor(this, leafText)
}

private fun tokenSetOf(vararg tokens: RsTokenType) = TokenSet.create(*tokens)

val RS_KEYWORDS = tokenSetOf(
    ABSTRACT, ALIGNOF, AS,
    BECOME, BOX, BREAK,
    CONST, CONTINUE, CRATE, CSELF,
    DO, DEFAULT,
    ELSE, ENUM, EXTERN,
    FALSE, FINAL, FN, FOR,
    IF, IMPL, IN,
    KW_MACRO,
    LET, LOOP,
    MATCH, MOD, MOVE, MUT,
    OFFSETOF, OVERRIDE,
    PRIV, PROC, PUB, PURE,
    REF, RETURN,
    SELF, SIZEOF, STATIC, STRUCT, SUPER,
    TRAIT, TRUE, TYPE_KW, TYPEOF,
    UNION, UNSAFE, UNSIZED, USE,
    VIRTUAL,
    WHERE, WHILE,
    YIELD
)

val RS_OPERATORS = tokenSetOf(
    AND, ANDEQ, ARROW, FAT_ARROW, SHA, COLON, COLONCOLON, COMMA, DIV, DIVEQ, DOT, DOTDOT, DOTDOTDOT, EQ, EQEQ, EXCL,
    EXCLEQ, GT, LT, MINUS, MINUSEQ, MUL, MULEQ, OR, OREQ, PLUS, PLUSEQ, REM, REMEQ, SEMICOLON, XOR, XOREQ, Q, AT,
    DOLLAR, GTGTEQ, GTGT, GTEQ, LTLTEQ, LTLT, LTEQ, OROR, ANDAND
)
