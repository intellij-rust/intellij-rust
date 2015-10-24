package org.rust.lang.tools

import com.google.gson.JsonParser
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.apache.commons.lang.StringEscapeUtils
import org.rust.lang.core.lexer.RustLexer
import org.rust.lang.core.lexer.RustTokenElementTypes
import java.io.File
import java.nio.charset.Charset

fun runRustcLexer(file: String): Array<String> {
    val rt = Runtime.getRuntime()
    rt.exec(arrayOf("cargo", "build", "--manifest-path", "rustc_lexer/Cargo.toml", "--release")).waitFor()
    return rt.exec(arrayOf("rustc_lexer/target/release/rustc_lexer", file))
            .inputStream.bufferedReader(Charset.defaultCharset()).lines().toArray { size -> arrayOfNulls<String>(size) }
}

class Token(val tok: IElementType, val range: IntRange)

fun processFile(file: String) {
    if (file.contains("parse-fail", false)) {
        return
    }
    var text = File(file).readText(Charset.defaultCharset())
    if (text[0] == '\uFEFF') {
        text = text.substring(1)
    }
    val bytes = text.toByteArray(Charset.defaultCharset())

    var prevToBytes = 0
    var prevTo = 0

    val rustcTokens = runRustcLexer(file).map { str ->
        val obj = JsonParser().parse(str).asJsonObject
        // Dirty hack to convert from UTF8 offsets to UTF16 offsets
        val fromBytes = obj.get("from").asInt
        val toBytes = obj.get("to").asInt
        assert(fromBytes == prevToBytes)
        val from = prevTo
        val to = from + String(bytes.copyOfRange(fromBytes, toBytes), Charset.defaultCharset()).length
        prevToBytes = toBytes
        prevTo = to
        val tok = obj.get("tok")
        val token: IElementType = if (tok.isJsonPrimitive) {
            when (tok.asString) {
                "AndAnd" -> RustTokenElementTypes.ANDAND
                "At" -> RustTokenElementTypes.AT
                "Colon" -> RustTokenElementTypes.COLON
                "Comma" -> RustTokenElementTypes.COMMA
                "Comment" -> {
                    when (text[from + 1]) {
                        '*' -> RustTokenElementTypes.BLOCK_COMMENT
                        '/' -> RustTokenElementTypes.EOL_COMMENT
                        else -> {
                            System.err.println("Unknown rustc token type: $tok")
                            TokenType.BAD_CHARACTER
                        }
                    }
                }
                "Dollar" -> RustTokenElementTypes.DOLLAR
                "Dot" -> RustTokenElementTypes.DOT
                "DotDot" -> RustTokenElementTypes.DOTDOT
                "DotDotDot" -> RustTokenElementTypes.DOTDOTDOT
                "Eq" -> RustTokenElementTypes.EQ
                "EqEq" -> RustTokenElementTypes.EQEQ
                "FatArrow" -> RustTokenElementTypes.FAT_ARROW
                "Ge" -> RustTokenElementTypes.GE
                "Gt" ->RustTokenElementTypes.GT
                "Le" -> RustTokenElementTypes.LE
                "Lt" -> RustTokenElementTypes.LT
                "ModSep" -> RustTokenElementTypes.COLONCOLON
                "Ne" -> RustTokenElementTypes.EXCLEQ
                "Not" -> RustTokenElementTypes.EXCL
                "OrOr" ->RustTokenElementTypes.OROR
                "Pound" -> RustTokenElementTypes.SHA
                "Question" -> RustTokenElementTypes.Q
                "RArrow" -> RustTokenElementTypes.ARROW
                "Semi" -> RustTokenElementTypes.SEMICOLON
                "Underscore" -> RustTokenElementTypes.UNDERSCORE
                "Whitespace" -> TokenType.WHITE_SPACE
                else -> {
                    System.err.println("Unknown rustc token type: $tok")
                    TokenType.BAD_CHARACTER
                }
            }
        } else {
            when (tok.asJsonObject.get("variant").asString) {
                "Ident" -> when (tok.asJsonObject.get("fields").asJsonArray.get(0).asString) {
                    "abstract" -> RustTokenElementTypes.ABSTRACT
                    "alignof" -> RustTokenElementTypes.ALIGNOF
                    "as" -> RustTokenElementTypes.AS
                    "become" -> RustTokenElementTypes.BECOME
                    "box" -> RustTokenElementTypes.BOX
                    "break" -> RustTokenElementTypes.BREAK
                    "const" -> RustTokenElementTypes.CONST
                    "continue" -> RustTokenElementTypes.CONTINUE
                    "crate" -> RustTokenElementTypes.CRATE
                    "do" -> RustTokenElementTypes.DO
                    "else" -> RustTokenElementTypes.ELSE
                    "enum" -> RustTokenElementTypes.ENUM
                    "extern" -> RustTokenElementTypes.EXTERN
                    "false" -> RustTokenElementTypes.FALSE
                    "final" -> RustTokenElementTypes.FINAL
                    "fn" -> RustTokenElementTypes.FN
                    "for" -> RustTokenElementTypes.FOR
                    "if" -> RustTokenElementTypes.IF
                    "impl" -> RustTokenElementTypes.IMPL
                    "in" -> RustTokenElementTypes.IN
                    "let" -> RustTokenElementTypes.LET
                    "loop" -> RustTokenElementTypes.LOOP
                    "macro" -> RustTokenElementTypes.MACRO
                    "match" -> RustTokenElementTypes.MATCH
                    "mod" -> RustTokenElementTypes.MOD
                    "move" -> RustTokenElementTypes.MOVE
                    "mut" -> RustTokenElementTypes.MUT
                    "offsetof" -> RustTokenElementTypes.OFFSETOF
                    "override" -> RustTokenElementTypes.OVERRIDE
                    "priv" -> RustTokenElementTypes.PRIV
                    "proc" -> RustTokenElementTypes.PROC
                    "pub" -> RustTokenElementTypes.PUB
                    "pure" -> RustTokenElementTypes.PURE
                    "ref" -> RustTokenElementTypes.REF
                    "return" -> RustTokenElementTypes.RETURN
                    "Self" -> RustTokenElementTypes.CSELF
                    "self" -> RustTokenElementTypes.SELF
                    "sizeof" -> RustTokenElementTypes.SIZEOF
                    "static" -> RustTokenElementTypes.STATIC
                    "struct" -> RustTokenElementTypes.STRUCT
                    "super" -> RustTokenElementTypes.SUPER
                    "trait" -> RustTokenElementTypes.TRAIT
                    "true" -> RustTokenElementTypes.TRUE
                    "type" -> RustTokenElementTypes.TYPE
                    "typeof" -> RustTokenElementTypes.TYPEOF
                    "unsafe" -> RustTokenElementTypes.UNSAFE
                    "unsized" -> RustTokenElementTypes.UNSIZED
                    "use" -> RustTokenElementTypes.USE
                    "virtual" -> RustTokenElementTypes.VIRTUAL
                    "where" -> RustTokenElementTypes.WHERE
                    "while" -> RustTokenElementTypes.WHILE
                    "yield" -> RustTokenElementTypes.YIELD
                    else -> RustTokenElementTypes.IDENTIFIER
                }
                "BinOp" -> when (tok.asJsonObject.get("fields").asJsonArray.get(0).asString) {
                    "And" -> RustTokenElementTypes.AND
                    "Caret" -> RustTokenElementTypes.XOR
                    "Minus" -> RustTokenElementTypes.MINUS
                    "Or" -> RustTokenElementTypes.OR
                    "Percent" -> RustTokenElementTypes.REM
                    "Plus" -> RustTokenElementTypes.PLUS
                    "Shl" -> RustTokenElementTypes.SHL
                    "Shr" -> RustTokenElementTypes.SHR
                    "Slash" -> RustTokenElementTypes.DIV
                    "Star" -> RustTokenElementTypes.MUL
                    else -> {
                        System.err.println("Unknown rustc token type: $tok")
                        TokenType.BAD_CHARACTER
                    }
                }
                "BinOpEq" -> when (tok.asJsonObject.get("fields").asJsonArray.get(0).asString) {
                    "And" -> RustTokenElementTypes.ANDEQ
                    "Caret" -> RustTokenElementTypes.XOREQ
                    "Minus" -> RustTokenElementTypes.MINUSEQ
                    "Or" -> RustTokenElementTypes.OREQ
                    "Percent" -> RustTokenElementTypes.REMEQ
                    "Plus" -> RustTokenElementTypes.PLUSEQ
                    "Shl" -> RustTokenElementTypes.SHLEQ
                    "Shr" -> RustTokenElementTypes.SHREQ
                    "Slash" -> RustTokenElementTypes.DIVEQ
                    "Star" -> RustTokenElementTypes.MULEQ
                    else -> {
                        System.err.println("Unknown rustc token type: $tok")
                        TokenType.BAD_CHARACTER
                    }
                }
                "OpenDelim" -> when (tok.asJsonObject.get("fields").asJsonArray.get(0).asString) {
                    "Brace" -> RustTokenElementTypes.LBRACE
                    "Bracket" -> RustTokenElementTypes.LBRACK
                    "Paren" -> RustTokenElementTypes.LPAREN
                    else -> {
                        System.err.println("Unknown rustc token type: $tok")
                        TokenType.BAD_CHARACTER
                    }
                }
                "CloseDelim" -> when (tok.asJsonObject.get("fields").asJsonArray.get(0).asString) {
                    "Brace" -> RustTokenElementTypes.RBRACE
                    "Bracket" -> RustTokenElementTypes.RBRACK
                    "Paren" -> RustTokenElementTypes.RPAREN
                    else -> {
                        System.err.println("Unknown rustc token type: $tok")
                        TokenType.BAD_CHARACTER
                    }
                }
                "Literal" -> when (tok.asJsonObject.get("fields").asJsonArray.get(0).asJsonObject.get("variant").asString) {
                    "Byte" -> RustTokenElementTypes.BYTE_LITERAL
                    "ByteStr" -> RustTokenElementTypes.BYTE_STRING_LITERAL
                    "ByteStrRaw" -> RustTokenElementTypes.RAW_BYTE_STRING_LITERAL
                    "Char" -> RustTokenElementTypes.CHAR_LITERAL
                    "Float" -> RustTokenElementTypes.FLOAT_LITERAL
                    "Integer" -> {
                        if (text.substring(from, to).endsWith("f32") || text.substring(from, to).endsWith("f64")) {
                            RustTokenElementTypes.FLOAT_LITERAL
                        } else {
                            RustTokenElementTypes.INTEGER_LITERAL
                        }
                    }
                    "Str_" -> RustTokenElementTypes.STRING_LITERAL
                    "StrRaw" -> RustTokenElementTypes.RAW_STRING_LITERAL
                    else -> {
                        System.err.println("Unknown rustc token type: $tok")
                        TokenType.BAD_CHARACTER
                    }
                }
                "Lifetime" -> {
                    if (text.substring(from, to) == "'static") {
                        RustTokenElementTypes.STATIC_LIFETIME
                    } else {
                        RustTokenElementTypes.LIFETIME
                    }
                }
                "DocComment" -> {
                    when (text[from + 2]) {
                        '!' -> RustTokenElementTypes.INNER_DOC_COMMENT
                        '/' -> RustTokenElementTypes.OUTER_DOC_COMMENT
                        '*' -> RustTokenElementTypes.OUTER_DOC_COMMENT
                        else -> {
                            System.err.println("Unknown rustc token type: $tok")
                            TokenType.BAD_CHARACTER
                        }
                    }
                }
                "Shebang" -> RustTokenElementTypes.SHEBANG_LINE
                else -> {
                    System.err.println("Unknown rustc token type: $tok")
                    TokenType.BAD_CHARACTER
                }
            }
        }
        Token(token, IntRange(from, to - 1))
    }
    val tokens = object : Iterator<Token> {
        val lexer = RustLexer()

        init {
            lexer.start(text)
        }

        override fun next(): Token {
            val t = Token(lexer.tokenType!!, IntRange(lexer.tokenStart, lexer.tokenEnd - 1))
            lexer.advance()
            return t
        }

        override fun hasNext(): Boolean {
            return lexer.tokenType != null
        }
    }.asSequence().toArrayList()
    if (rustcTokens.size == tokens.size && IntRange(0, rustcTokens.size - 1).all { i ->
        rustcTokens[i].tok == tokens[i].tok && rustcTokens[i].range == tokens[i].range
    }) {
//        println("File $file is ok")
        return
    }
    if (1L * rustcTokens.size * tokens.size > 1000000000) {
        println("File $file is too big")
        return
    }
    val editDist = Array(rustcTokens.size + 1, { IntArray(tokens.size + 1) })
    for (i in editDist.indices.reversed()) {
        for (j in editDist[i].indices.reversed()) {
            if (i == rustcTokens.size || j == tokens.size) {
                editDist[i][j] = rustcTokens.size - i + tokens.size - j
                continue
            }
            editDist[i][j] = Math.min(editDist[i][j + 1], editDist[i + 1][j]) + 1
            if (rustcTokens[i].tok == tokens[j].tok) {
                editDist[i][j] = Math.min(editDist[i][j], editDist[i + 1][j + 1])
            }
        }
    }
    println("Processing file $file")
    if (editDist[0][0] == 0) {
        return
    }
    println("Edit distance: ${editDist[0][0]}")
    var i = 0
    var j = 0
    fun toStr(tok: Token): String {
        return "${tok.tok}@${tok.range}(${StringEscapeUtils.escapeJava(text.substring(tok.range))})"
    }
    while (i < rustcTokens.size || j < tokens.size) {
        if (i < rustcTokens.size && j < tokens.size && rustcTokens[i].tok == tokens[j].tok && editDist[i][j] == editDist[i + 1][j + 1]) {
            if (rustcTokens[i].range != tokens[j].range) {
                println("Tokens mismatch ${toStr(rustcTokens[i])} != ${toStr(tokens[j])}")
            }
            i++
            j++
            continue
        }
        if (i < rustcTokens.size && editDist[i][j] == editDist[i + 1][j] + 1) {
            println("Missing token ${toStr(rustcTokens[i])}")
            i++
        } else {
            assert(editDist[i][j] == editDist[i][j + 1] + 1)
            println("Extra token ${toStr(tokens[j])}")
            j++
        }
    }
}


fun main(args: Array<String>) {
    val file = args[0]
    FileTreeWalk(File(file)).forEach { f ->
        if (!f.isDirectory && f.extension == "rs") {
            processFile(f.path)
        }
    }
}