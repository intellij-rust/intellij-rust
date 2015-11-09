package org.rust.lang.core.lexer

import com.intellij.lexer.Lexer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.testFramework.LexerTestCase
import java.io.File
import java.io.IOException
import java.nio.file.Paths


public class RustLexingTestCase : LexerTestCase() {
    override fun getDirPath(): String {
        val home = Paths.get(PathManager.getHomePath()).toAbsolutePath()
        val testData = Paths.get("testData", "org", "rust", "lang", "core", "lexer", "fixtures").toAbsolutePath()

        // XXX: unfortunately doFileTest will look for the file relative to the home directory of
        // the test instance of IDEA, so let's cook a dirPath starting with several ../../
        return home.relativize(testData).toString()
    }

    override fun createLexer(): Lexer? = RustLexer()

    // NOTE(matkad): this is basically a copy-paste of doFileTest.
    // The only difference is that encoding is set to utf-8
    fun doTest() {
        val fileName = PathManager.getHomePath() + "/" + dirPath + "/" + getTestName(true) + ".rs"
        var text = ""
        try {
            val fileText = FileUtil.loadFile(File(fileName), CharsetToolkit.UTF8);
            text = StringUtil.convertLineSeparators(if (shouldTrim()) fileText.trim() else fileText);
        } catch (e: IOException) {
            fail("can't load file " + fileName + ": " + e.message);
        }
        doTest(text);
    }

    fun testComments() = doTest()
    fun testShebang() = doTest()
    fun testFloat() = doTest()
    fun testIdentifiers() = doTest()
    fun testCharLiterals() = doTest()
    fun testStringLiterals() = doTest()
    fun testByteLiterals() = doTest()
}
