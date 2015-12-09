package org.rust.lang.core.lexer

import com.intellij.lexer.Lexer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.testFramework.LexerTestCase
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.annotations.NonNls
import org.rust.lang.RustTestCase
import org.rust.lang.pathToGoldTestFile
import org.rust.lang.pathToSourceTestFile
import java.io.File
import java.io.IOException
import java.nio.file.Paths


public class RustLexingTestCase : LexerTestCase(), RustTestCase {
    override fun getDirPath(): String {
        throw UnsupportedOperationException()
    }

    override fun getTestDataPath(): String = "org/rust/lang/core/lexer/fixtures"

    override fun createLexer(): Lexer? = RustLexer()

    // NOTE(matkad): this is basically a copy-paste of doFileTest.
    // The only difference is that encoding is set to utf-8
    fun doTest() {
        val filePath = pathToSourceTestFile(getTestName(true))
        var text = ""
        try {
            val fileText = FileUtil.loadFile(filePath.toFile(), CharsetToolkit.UTF8);
            text = StringUtil.convertLineSeparators(if (shouldTrim()) fileText.trim() else fileText);
        } catch (e: IOException) {
            fail("can't load file " + filePath + ": " + e.message);
        }
        doTest(text);
    }

    protected override fun doTest(@NonNls text: String, expected: String?, lexer: Lexer) {
        val result = printTokens(text, 0, lexer)
        if (expected != null) {
            UsefulTestCase.assertSameLines(expected, result)
        } else {
            UsefulTestCase.assertSameLinesWithFile(pathToGoldTestFile(getTestName(true)).toFile().canonicalPath, result)
        }
    }

    fun testComments() = doTest()
    fun testShebang() = doTest()
    fun testFloat() = doTest()
    fun testIdentifiers() = doTest()
    fun testCharLiterals() = doTest()
    fun testStringLiterals() = doTest()
    fun testByteLiterals() = doTest()
}
