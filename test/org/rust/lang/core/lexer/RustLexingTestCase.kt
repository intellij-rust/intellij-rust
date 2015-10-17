package org.rust.lang.core.lexer

import com.intellij.lexer.Lexer
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.LexerTestCase
import org.rust.lang.RustFileType
import java.nio.file.Paths


public class RustLexingTestCase : LexerTestCase() {

    override fun getDirPath(): String {
        val home = Paths.get(PathManager.getHomePath()).toAbsolutePath()
        val testData = Paths.get("testData", "lexer").toAbsolutePath()

        // XXX: unfortunately doFileTest will look for the file relative to the home directory of
        // the test instance of IDEA, so let's cook a dirPath starting with several ../../
        return home.relativize(testData).toString()
    }

    override fun createLexer(): Lexer? = RustLexer()

    val ext = RustFileType.DEFAULTS.EXTENSION

    // @formatter:off
    fun testComments()          { doFileTest(ext) }
    fun testShebang()           { doFileTest(ext) }
    fun testFloat()             { doFileTest(ext) }
    fun testIdentifiers()       { doFileTest(ext) }
    fun testCharLiterals()      { doFileTest(ext) }
    fun testStringLiterals()    { doFileTest(ext) }
    fun testByteLiterals()      { doFileTest(ext) }
    // @formatter:on
}