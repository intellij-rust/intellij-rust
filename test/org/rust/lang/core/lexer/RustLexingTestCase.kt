package org.rust.lang.core.lexer

import com.intellij.lang.ParserDefinition
import com.intellij.lexer.Lexer
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.LexerTestCase
import org.rust.lang.RustFileType
import org.rust.lang.RustTestUtils
import org.rust.lang.core.RustParserDefinition

public class RustLexingTestCase : LexerTestCase() {

    init {
        RustTestUtils.setTestDataPath()
    }

    override fun getDirPath(): String? = "lexer"

    override fun createLexer(): Lexer? = RustLexer()

    val ext = RustFileType.DEFAULTS.EXTENSION

    fun testComments()  { doFileTest(ext) }
    fun testShebang()   { doFileTest(ext) }

}