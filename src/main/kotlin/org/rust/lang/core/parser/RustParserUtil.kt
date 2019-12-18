/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.parser

import com.intellij.lang.LighterASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderUtil
import com.intellij.lang.WhitespacesAndCommentsBinder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.lexer.Lexer
import com.intellij.openapi.util.Key
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.util.BitUtil
import org.rust.lang.core.parser.RustParserDefinition.Companion.EOL_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_BLOCK_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_EOL_DOC_COMMENT
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.stdext.makeBitMask

@Suppress("UNUSED_PARAMETER")
object RustParserUtil : GeneratedParserUtilBase() {
    enum class PathParsingMode {
        /**
         * Accepts paths like `Foo::<i32>`
         * Should be used to parse references to values
         */
        VALUE,
        /**
         * Accepts paths like `Foo<i32>`, `Foo::<i32>`, Fn(i32) -> i32 and Fn::(i32) -> i32
         * Should be used to parse type and trait references
         */
        TYPE,
        /**
         * Accepts paths like `Foo`
         * Should be used to parse paths where type args cannot be specified: `use` items, macro calls, etc.
         */
        NO_TYPE_ARGS
    }
    enum class BinaryMode { ON, OFF }
    enum class MacroCallParsingMode(val semicolon: Boolean, val pin: Boolean, val forbidExprSpecialMacros: Boolean) {
        ITEM(semicolon = true, pin = true, forbidExprSpecialMacros = false),
        BLOCK(semicolon = true, pin = false, forbidExprSpecialMacros = true),
        EXPR(semicolon = false, pin = true, forbidExprSpecialMacros = false)
    }

    private val FLAGS: Key<Int> = Key("RustParserUtil.FLAGS")
    private var PsiBuilder.flags: Int
        get() = getUserData(FLAGS) ?: DEFAULT_FLAGS
        set(value) = putUserData(FLAGS, value)

    private fun PsiBuilder.withFlag(flag: Int, mode: BinaryMode, block: PsiBuilder.() -> Boolean): Boolean {
        val oldFlags = flags
        val newFlags = BitUtil.set(oldFlags, flag, mode == BinaryMode.ON)
        flags = newFlags
        val result = block()
        flags = oldFlags
        return result
    }


    private val STRUCT_ALLOWED: Int = makeBitMask(1)
    private val TYPE_QUAL_ALLOWED: Int = makeBitMask(2)
    private val STMT_EXPR_MODE: Int = makeBitMask(3)

    private val PATH_VALUE: Int = makeBitMask(4)
    private val PATH_TYPE: Int = makeBitMask(5)
    private val PATH_NO_TYPE_ARGS: Int = makeBitMask(6)

    private val MACRO_BRACE_PARENS: Int = makeBitMask(7)
    private val MACRO_BRACE_BRACKS: Int = makeBitMask(8)
    private val MACRO_BRACE_BRACES: Int = makeBitMask(9)
    private fun setPathMod(flags: Int, mode: PathParsingMode): Int {
        val flag = when (mode) {
            PathParsingMode.VALUE -> PATH_VALUE
            PathParsingMode.TYPE -> PATH_TYPE
            PathParsingMode.NO_TYPE_ARGS -> PATH_NO_TYPE_ARGS
        }
        return flags and (PATH_VALUE or PATH_TYPE or PATH_NO_TYPE_ARGS).inv() or flag
    }

    private fun getPathMod(flags: Int): PathParsingMode = when {
        BitUtil.isSet(flags, PATH_VALUE) -> PathParsingMode.VALUE
        BitUtil.isSet(flags, PATH_TYPE) -> PathParsingMode.TYPE
        BitUtil.isSet(flags, PATH_NO_TYPE_ARGS) -> PathParsingMode.NO_TYPE_ARGS
        else -> error("Path parsing mode not set")
    }

    private fun setMacroBraces(flags: Int, mode: MacroBraces): Int {
        val flag = when (mode) {
            MacroBraces.PARENS -> MACRO_BRACE_PARENS
            MacroBraces.BRACKS -> MACRO_BRACE_BRACKS
            MacroBraces.BRACES -> MACRO_BRACE_BRACES
        }
        return flags and (MACRO_BRACE_PARENS or MACRO_BRACE_BRACKS or MACRO_BRACE_BRACES).inv() or flag
    }

    private fun getMacroBraces(flags: Int): MacroBraces? = when {
        BitUtil.isSet(flags, MACRO_BRACE_PARENS) -> MacroBraces.PARENS
        BitUtil.isSet(flags, MACRO_BRACE_BRACKS) -> MacroBraces.BRACKS
        BitUtil.isSet(flags, MACRO_BRACE_BRACES) -> MacroBraces.BRACES
        else -> null
    }

    private val DEFAULT_FLAGS: Int = STRUCT_ALLOWED or TYPE_QUAL_ALLOWED

    @JvmField
    val ADJACENT_LINE_COMMENTS = WhitespacesAndCommentsBinder { tokens, _, getter ->
        var candidate = tokens.size
        for (i in 0 until tokens.size) {
            val token = tokens[i]
            if (OUTER_BLOCK_DOC_COMMENT == token || OUTER_EOL_DOC_COMMENT == token) {
                candidate = minOf(candidate, i)
                break
            }
            if (EOL_COMMENT == token) {
                candidate = minOf(candidate, i)
            }
            if (TokenType.WHITE_SPACE == token && "\n\n" in getter[i]) {
                candidate = tokens.size
            }
        }
        candidate
    }

    private val LEFT_BRACES = tokenSetOf(LPAREN, LBRACE, LBRACK)
    private val RIGHT_BRACES = tokenSetOf(RPAREN, RBRACE, RBRACK)

    //
    // Helpers
    //

    @JvmStatic
    fun checkStructAllowed(b: PsiBuilder, level: Int): Boolean = BitUtil.isSet(b.flags, STRUCT_ALLOWED)

    @JvmStatic
    fun checkTypeQualAllowed(b: PsiBuilder, level: Int): Boolean = BitUtil.isSet(b.flags, TYPE_QUAL_ALLOWED)

    @JvmStatic
    fun checkBraceAllowed(b: PsiBuilder, level: Int): Boolean =
        b.tokenType != LBRACE || checkStructAllowed(b, level)

    @JvmStatic
    fun structLiterals(b: PsiBuilder, level: Int, mode: BinaryMode, parser: Parser): Boolean =
        b.withFlag(STRUCT_ALLOWED, mode) { parser.parse(this, level) }

    @JvmStatic
    fun typeQuals(b: PsiBuilder, level: Int, mode: BinaryMode, parser: Parser): Boolean =
        b.withFlag(TYPE_QUAL_ALLOWED, mode) { parser.parse(this, level) }

    /**
     * Controls the difference between
     *
     *     ```
     *     // bit and
     *     let _ = {1} & x;
     *     ```
     * and
     *
     *     ```
     *     // two statements: block expression {1} and unary reference expression &x
     *     {1} & x;
     *     ```
     *
     * See `Restrictions::RESTRICTION_STMT_EXPR` in libsyntax
     */
    @JvmStatic
    fun stmtMode(b: PsiBuilder, level: Int, mode: BinaryMode, parser: Parser): Boolean =
        b.withFlag(STMT_EXPR_MODE, mode) { parser.parse(this, level) }

    @JvmStatic
    fun isCompleteBlockExpr(b: PsiBuilder, level: Int): Boolean =
        isBlock(b, level) && BitUtil.isSet(b.flags, STMT_EXPR_MODE)

    @JvmStatic
    fun isBlock(b: PsiBuilder, level: Int): Boolean {
        val m = b.latestDoneMarker ?: return false
        return m.tokenType in RS_BLOCK_LIKE_EXPRESSIONS || m.isBracedMacro(b)
    }

    @JvmStatic
    fun pathMode(b: PsiBuilder, level: Int, mode: PathParsingMode, parser: Parser): Boolean {
        val oldFlags = b.flags
        val newFlags = setPathMod(oldFlags, mode)
        b.flags = newFlags
        check(getPathMod(b.flags) == mode)
        val result = parser.parse(b, level)
        b.flags = oldFlags
        return result
    }

    @JvmStatic
    fun isPathMode(b: PsiBuilder, level: Int, mode: PathParsingMode): Boolean =
        mode == getPathMod(b.flags)

    @JvmStatic
    fun unpairedToken(b: PsiBuilder, level: Int): Boolean =
        when (b.tokenType) {
            LBRACE, RBRACE,
            LPAREN, RPAREN,
            LBRACK, RBRACK -> false
            null -> false // EOF
            else -> {
                collapsedTokenType(b)?.let { (tokenType, size) ->
                    val marker = b.mark()
                    PsiBuilderUtil.advance(b, size)
                    marker.collapse(tokenType)
                } ?: b.advanceLexer()
                true
            }
        }

    @JvmStatic
    fun macroBindingGroupSeparatorToken(b: PsiBuilder, level: Int): Boolean =
        when (b.tokenType) {
            PLUS, MUL, Q -> false
            else -> unpairedToken(b, level)
        }

    @JvmStatic
    fun macroSemicolon(b: PsiBuilder, level: Int): Boolean {
        val m = b.latestDoneMarker ?: return false
        b.tokenText
        if (b.originalText[m.endOffset - 1] == '}') return true
        return consumeToken(b, SEMICOLON)
    }

    @JvmStatic
    fun macroIdentifier(b: PsiBuilder, level: Int): Boolean =
        when (b.tokenType) {
            IDENTIFIER, in RS_KEYWORDS -> {
                b.advanceLexer()
                true
            }
            else -> false
        }

    @JvmStatic
    fun tupleOrParenType(b: PsiBuilder, level: Int, typeReference: Parser, tupeTypeUpper: Parser): Boolean {
        val tupleOrParens: PsiBuilder.Marker = enter_section_(b)

        if (!consumeTokenSmart(b, LPAREN)) {
            exit_section_(b, tupleOrParens, null, false)
            return false
        }

        val firstType = enter_section_(b)

        if (!typeReference.parse(b, level)) {
            exit_section_(b, firstType, null, false)
            exit_section_(b, tupleOrParens, null, false)
            return false
        }
        if (consumeTokenFast(b, RPAREN)) {
            exit_section_(b, firstType, null, true)
            exit_section_(b, tupleOrParens, null, true)
            return true
        }
        exit_section_(b, firstType, TYPE_REFERENCE, true)
        val result = tupeTypeUpper.parse(b, level)
        exit_section_(b, tupleOrParens, TUPLE_TYPE, result)
        return result
    }

    @JvmStatic
    fun baseOrTraitType(
        b: PsiBuilder,
        level: Int,
        pathP: Parser,
        implicitTraitTypeP: Parser,
        traitTypeUpperP: Parser
    ): Boolean {
        val baseOrTrait = enter_section_(b)
        val polybound = enter_section_(b)
        val bound = enter_section_(b)
        val traitRef = enter_section_(b)

        if (!pathP.parse(b, level + 1) || nextTokenIs(b, EXCL)) {
            // May be it is lifetime `'a` or `for<'a>` or `foo!()`
            exit_section_(b, traitRef, null, false)
            exit_section_(b, bound, null, false)
            exit_section_(b, polybound, null, false)
            exit_section_(b, baseOrTrait, null, false)
            return implicitTraitTypeP.parse(b, level)
        }

        if (!nextTokenIs(b, PLUS)) {
            exit_section_(b, traitRef, null, true)
            exit_section_(b, bound, null, true)
            exit_section_(b, polybound, null, true)
            exit_section_(b, baseOrTrait, BASE_TYPE, true)
            return true
        }

        exit_section_(b, traitRef, TRAIT_REF, true)
        exit_section_(b, bound, BOUND, true)
        exit_section_(b, polybound, POLYBOUND, true)
        val result = traitTypeUpperP.parse(b, level + 1)
        exit_section_(b, baseOrTrait, TRAIT_TYPE, result)
        return result
    }

    private val SPECIAL_MACRO_PARSERS: Map<String, (PsiBuilder, Int) -> Boolean>
    private val SPECIAL_EXPR_MACROS: Set<String>

    init {
        val specialParsers = hashMapOf<String, (PsiBuilder, Int) -> Boolean>()
        val exprMacros = hashSetOf<String>()
        SPECIAL_MACRO_PARSERS = specialParsers
        SPECIAL_EXPR_MACROS = exprMacros

        fun put(parser: (PsiBuilder, Int) -> Boolean, isExpr: Boolean, vararg keys: String) {
            for (name in keys) {
                check(name !in specialParsers) { "$name was already added" }
                specialParsers[name] = parser
                if (isExpr) {
                    exprMacros += name
                }
            }
        }

        put(RustParser::ExprMacroArgument, true, "try", "await", "dbg")
        put(RustParser::FormatMacroArgument, true, "format", "format_args", "write", "writeln", "print", "println",
            "eprint", "eprintln", "panic", "unimplemented", "unreachable", "todo")
        put(RustParser::AssertMacroArgument, true, "assert", "debug_assert", "assert_eq", "assert_ne",
            "debug_assert_eq", "debug_assert_ne")
        put(RustParser::VecMacroArgument, true, "vec")
        put(RustParser::LogMacroArgument, true, "trace", "log", "warn", "debug", "error", "info")
        put(RustParser::IncludeMacroArgument, true, "include_str", "include_bytes")
        put(RustParser::IncludeMacroArgument, false, "include")
        put(RustParser::ConcatMacroArgument, true, "concat")
        put(RustParser::EnvMacroArgument, true, "env")
    }

    @JvmStatic
    fun parseMacroCall(b: PsiBuilder, level: Int, mode: MacroCallParsingMode): Boolean {
        if (!RustParser.AttrsAndVis(b, level + 1)) return false

        val macroName = lookupSimpleMacroName(b)
        if (mode.forbidExprSpecialMacros && macroName in SPECIAL_EXPR_MACROS) return false

        if (!RustParser.PathWithoutTypeArgs(b, level + 1) || !consumeToken(b, EXCL)) {
            return false
        }

        // foo! bar {}
        //      ^ this ident
        val hasIdent = consumeTokenFast(b, IDENTIFIER)

        val braceKind = b.tokenType?.let { MacroBraces.fromToken(it) }

        if (macroName != null && !hasIdent && braceKind != null) { // try special macro
            val specialParser = SPECIAL_MACRO_PARSERS[macroName]
            if (specialParser != null && specialParser(b, level + 1)) {
                if (braceKind.needsSemicolon && mode.semicolon && !consumeToken(b, SEMICOLON)) {
                    b.error("`;` expected, got '${b.tokenText}'")
                    return mode.pin
                }
                return true
            }
        }

        if (braceKind == null || !parseMacroArgumentLazy(b, level + 1)) {
            b.error("<macro argument> expected, got '${b.tokenText}'")
            return mode.pin
        }
        if (braceKind.needsSemicolon && mode.semicolon && !consumeToken(b, SEMICOLON)) {
            b.error("`;` expected, got '${b.tokenText}'")
            return mode.pin
        }
        return true
    }

    // foo ! ();
    // ^ this name
    private fun lookupSimpleMacroName(b: PsiBuilder): String? {
        return if (b.tokenType == IDENTIFIER) {
            val nextTokenIsExcl = b.probe {
                b.advanceLexer()
                b.tokenType == EXCL
            }
            if (nextTokenIsExcl) b.tokenText else null
        } else {
            null
        }
    }

    @JvmStatic
    fun gtgteqImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, GTGTEQ, GT, GT, EQ)

    @JvmStatic
    fun gtgtImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, GTGT, GT, GT)

    @JvmStatic
    fun gteqImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, GTEQ, GT, EQ)

    @JvmStatic
    fun ltlteqImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, LTLTEQ, LT, LT, EQ)

    @JvmStatic
    fun ltltImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, LTLT, LT, LT)

    @JvmStatic
    fun lteqImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, LTEQ, LT, EQ)

    @JvmStatic
    fun ororImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, OROR, OR, OR)

    @JvmStatic
    fun andandImpl(b: PsiBuilder, level: Int): Boolean = collapse(b, ANDAND, AND, AND)

    private val DEFAULT_NEXT_ELEMENTS: TokenSet = tokenSetOf(EXCL)
    private val ASYNC_NEXT_ELEMENTS: TokenSet = tokenSetOf(LBRACE, MOVE, OR)
    private val TRY_NEXT_ELEMENTS: TokenSet = tokenSetOf(LBRACE)

    @JvmStatic
    fun defaultKeyword(b: PsiBuilder, level: Int): Boolean = contextualKeyword(b, "default", DEFAULT)

    @JvmStatic
    fun unionKeyword(b: PsiBuilder, level: Int): Boolean = contextualKeyword(b, "union", UNION)

    @JvmStatic
    fun autoKeyword(b: PsiBuilder, level: Int): Boolean = contextualKeyword(b, "auto", AUTO)

    @JvmStatic
    fun dynKeyword(b: PsiBuilder, level: Int): Boolean = contextualKeyword(b, "dyn", DYN)

    @JvmStatic
    fun asyncKeyword(b: PsiBuilder, level: Int): Boolean =
        contextualKeyword(b, "async", ASYNC)

    @JvmStatic
    fun asyncBlockKeyword(b: PsiBuilder, level: Int): Boolean =
        contextualKeyword(b, "async", ASYNC) { it in ASYNC_NEXT_ELEMENTS }

    @JvmStatic
    fun tryKeyword(b: PsiBuilder, level: Int): Boolean =
        contextualKeyword(b, "try", TRY) { it in TRY_NEXT_ELEMENTS }

    @JvmStatic
    private fun collapse(b: PsiBuilder, tokenType: IElementType, vararg parts: IElementType): Boolean {
        // We do not want whitespace between parts, so firstly we do raw lookup for each part,
        // and when we make sure that we have desired token, we consume and collapse it.
        parts.forEachIndexed { i, tt ->
            if (b.rawLookup(i) != tt) return false
        }
        val marker = b.mark()
        PsiBuilderUtil.advance(b, parts.size)
        marker.collapse(tokenType)
        return true
    }

    /** Collapses contextual tokens (like &&) to a single token */
    fun collapsedTokenType(b: PsiBuilder): Pair<IElementType, Int>? {
        return when (b.tokenType) {
            GT -> when (b.rawLookup(1)) {
                GT -> when (b.rawLookup(2)) {
                    EQ -> GTGTEQ to 3
                    else -> GTGT to 2
                }
                EQ -> GTEQ to 2
                else -> null
            }
            LT -> when (b.rawLookup(1)) {
                LT -> when (b.rawLookup(2)) {
                    EQ -> LTLTEQ to 3
                    else -> LTLT to 2
                }
                EQ -> LTEQ to 2
                else -> null
            }
            OR -> when (b.rawLookup(1)) {
                OR -> OROR to 2
                else -> null
            }
            AND -> when (b.rawLookup(1)) {
                AND -> ANDAND to 2
                else -> null
            }
            else -> null
        }
    }

    private fun LighterASTNode.isBracedMacro(b: PsiBuilder): Boolean =
        tokenType == RsElementTypes.MACRO_EXPR &&
            '}' == b.originalText.subSequence(startOffset, endOffset).findLast { it == '}' || it == ']' || it == ')' }

    private fun contextualKeyword(
        b: PsiBuilder,
        keyword: String,
        elementType: IElementType,
        nextElementPredicate: (IElementType?) -> Boolean = { it !in DEFAULT_NEXT_ELEMENTS }
    ): Boolean {
        // Tricky: the token can be already remapped by some previous rule that was backtracked
        if (b.tokenType == elementType ||
            b.tokenText == keyword && b.tokenType == IDENTIFIER && nextElementPredicate(b.lookAhead(1))) {
            b.remapCurrentToken(elementType)
            b.advanceLexer()
            return true
        }
        return false
    }

    @JvmStatic
    fun parseMacroArgumentLazy(builder: PsiBuilder, level: Int): Boolean =
        parseTokenTreeLazy(builder, level, MACRO_ARGUMENT)

    @JvmStatic
    fun parseMacroBodyLazy(builder: PsiBuilder, level: Int): Boolean =
        parseTokenTreeLazy(builder, level, MACRO_BODY)

    private fun parseTokenTreeLazy(builder: PsiBuilder, level: Int, tokenTypeToCollapse: IElementType): Boolean {
        val firstToken = builder.tokenType
        if (firstToken == null || firstToken !in LEFT_BRACES) return false
        val rightBrace = MacroBraces.fromTokenOrFail(firstToken).closeToken
        PsiBuilderUtil.parseBlockLazy(builder, firstToken, rightBrace, tokenTypeToCollapse)
        return true
    }

    fun hasProperTokenTreeBraceBalance(text: CharSequence, lexer: Lexer): Boolean {
        lexer.start(text)

        val firstToken = lexer.tokenType
        if (firstToken == null || firstToken !in LEFT_BRACES) return false
        val rightBrace = MacroBraces.fromTokenOrFail(firstToken).closeToken

        return PsiBuilderUtil.hasProperBraceBalance(text, lexer, firstToken, rightBrace)
    }

    @JvmStatic
    fun parseAnyBraces(b: PsiBuilder, level: Int, param: Parser): Boolean {
        val firstToken = b.tokenType ?: return false
        if (firstToken !in LEFT_BRACES) return false
        val leftBrace = MacroBraces.fromTokenOrFail(firstToken)
        val pos = b.mark()
        b.advanceLexer() // Consume '{' or '(' or '['
        return b.withRootBrace(leftBrace) { rootBrace ->
            if (!param.parse(b, level + 1)) {
                pos.rollbackTo()
                return false
            }

            val lastToken = b.tokenType
            if (lastToken == null || lastToken !in RIGHT_BRACES) {
                b.error("'${leftBrace.closeText}' expected")
                return pos.close(lastToken == null)
            }

            var rightBrace = MacroBraces.fromToken(lastToken)
            if (rightBrace == leftBrace) {
                b.advanceLexer() // Consume '}' or ')' or ']'
            } else {
                b.error("'${leftBrace.closeText}' expected")
                if (leftBrace == rootBrace) {
                    // Recovery loop. Consume everything until [rightBrace] is [leftBrace]
                    while (rightBrace != leftBrace && !b.eof()) {
                        b.advanceLexer()
                        val tokenType = b.tokenType ?: break
                        rightBrace = MacroBraces.fromToken(tokenType)
                    }
                    b.advanceLexer()
                }
            }

            pos.drop()
            return true
        }
    }

    /** Saves [currentBrace] as root brace if it is not set yet; applies the root brace to [f] */
    private inline fun PsiBuilder.withRootBrace(currentBrace: MacroBraces, f: (MacroBraces) -> Boolean): Boolean {
        val oldFlags = flags
        val oldRootBrace = getMacroBraces(oldFlags)
        if (oldRootBrace == null) {
            flags = setMacroBraces(oldFlags, currentBrace)
        }
        try {
            return f(oldRootBrace ?: currentBrace)
        } finally {
            flags = oldFlags
        }
    }
}
