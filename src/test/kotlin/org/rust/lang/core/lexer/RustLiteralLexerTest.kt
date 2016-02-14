package org.rust.lang.core.lexer

import com.intellij.lexer.Lexer
import com.intellij.psi.tree.IElementType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.rust.lang.core.psi.LiteralTokenTypes

abstract class RustLiteralLexerTestBase(
    private val input: String,
    private val expectedOutput: Iterable<Pair<IElementType, String>>) : RustLexingTestCaseBase() {

    override fun doTest() = doTest(input, printTokens(expectedOutput))

    override fun getTestDataPath(): String = error("unreachable")

    private fun printTokens(tokens: Iterable<Pair<IElementType, String>>): String {
        val sb = StringBuilder()
        for ((type, text) in tokens) {
            sb.append("$type ('$text')\n")
        }
        return sb.toString()
    }
}

@RunWith(Parameterized::class)
class RustNumericLiteralLexerTest(
    input: String,
    expectedOutput: Iterable<Pair<IElementType, String>>) : RustLiteralLexerTestBase(input, expectedOutput) {

    override fun createLexer(): Lexer = RustLiteralLexer.forNumericLiterals()

    @Test
    fun test() = doTest()

    companion object {
        @Parameterized.Parameters(name = "{index}: {0}")
        @JvmStatic fun data(): Collection<Array<Any>> = listOf(
            //@formatter:off
            arrayOf("123i32",      listOf( v("123"),      s("i32") )),
            arrayOf("0u",          listOf( v("0"),        s("u")   )),
            arrayOf("0",           listOf( v("0")                  )),
            arrayOf("-12f32",      listOf( v("-12"),      s("f32") )),
            arrayOf("-12.124f32",  listOf( v("-12.124"),  s("f32") )),
            arrayOf("1.0e10",      listOf( v("1.0e10")             )),
            arrayOf("1.0e",        listOf( v("1.0e")               )),
            arrayOf("1.0ee",       listOf( v("1.0e"),     s("e")   )),
            arrayOf("0xABC",       listOf( v("0xABC")              )),
            arrayOf("0xABCi64",    listOf( v("0xABC"),    s("i64") )),
            arrayOf("2.",          listOf( v("2.")                 )),
            arrayOf("0x",          listOf( v("0x")                 )),
            arrayOf("1_______",    listOf( v("1_______")           )),
            arrayOf("1_______i32", listOf( v("1_______"), s("i32") ))
            //@formatter:on
        )
    }
}

@RunWith(Parameterized::class)
class RustQuotedLiteralLexerTest(
    input: String,
    expectedOutput: Iterable<Pair<IElementType, String>>) : RustLiteralLexerTestBase(input, expectedOutput) {

    override fun createLexer(): Lexer = RustLiteralLexer.forCharLiterals()

    @Test
    fun test() = doTest()

    companion object {
        @Parameterized.Parameters(name = "{index}: {0}")
        @JvmStatic fun data(): Collection<Array<Any>> = listOf(
            //@formatter:off
            arrayOf("'a'suf",       listOf(         d("'"), v("a"),       d("'"), s("suf") )),
            arrayOf("b'a'",         listOf( p("b"), d("'"), v("a"),       d("'")           )),
            arrayOf("b'a'suf",      listOf( p("b"), d("'"), v("a"),       d("'"), s("suf") )),
            arrayOf("'a",           listOf(         d("'"), v("a")                         )),
            arrayOf("''",           listOf(         d("'"),               d("'")           )),
            arrayOf("'\\\\'",       listOf(         d("'"), v("\\\\"),    d("'")           )),
            arrayOf("'\\'",         listOf(         d("'"), v("\\'")                       )),
            arrayOf("''a",          listOf(         d("'"),               d("'"), s("a")   )),
            arrayOf("'\\\\'a",      listOf(         d("'"), v("\\\\"),    d("'"), s("a")   )),
            arrayOf("'\\'a",        listOf(         d("'"), v("\\'a")                      )),
            arrayOf("'\\\\\\'a",    listOf(         d("'"), v("\\\\\\'a")                  ))
            //@formatter:on
        )
    }
}

@RunWith(Parameterized::class)
class RustRawStringLiteralLexerTest(
    input: String,
    expectedOutput: Iterable<Pair<IElementType, String>>) : RustLiteralLexerTestBase(input, expectedOutput) {

    override fun createLexer(): Lexer = RustLiteralLexer.forRawStringLiterals()

    @Test
    fun test() = doTest()

    companion object {
        @Parameterized.Parameters(name = "{index}: {0}")
        @JvmStatic fun data(): Collection<Array<Any>> = listOf(
            //@formatter:off
            arrayOf("r\"a\"suf",          listOf(p("r"),  d("\""),    v("a"),      d("\""),    s("suf") )),
            arrayOf("br\"a\"suf",         listOf(p("br"), d("\""),    v("a"),      d("\""),    s("suf") )),
            arrayOf("r\"a\"",             listOf(p("r"),  d("\""),    v("a"),      d("\"")              )),
            arrayOf("r###\"aaa",          listOf(p("r"),  d("###\""), v("aaa")                          )),
            arrayOf("r###\"aaa\"##",      listOf(p("r"),  d("###\""), v("aaa\"##")                      )),
            arrayOf("r###\"\"###",        listOf(p("r"),  d("###\""),              d("\"###")           )),
            arrayOf("r###\"a\"##a\"###s", listOf(p("r"),  d("###\""), v("a\"##a"), d("\"###"), s("s")   ))
            //@formatter:on
        )
    }
}

private fun p(str: String): Pair<IElementType, String> = LiteralTokenTypes.PREFIX to str
private fun d(str: String): Pair<IElementType, String> = LiteralTokenTypes.DELIMITER to str
private fun v(str: String): Pair<IElementType, String> = LiteralTokenTypes.VALUE to str
private fun s(str: String): Pair<IElementType, String> = LiteralTokenTypes.SUFFIX to str
