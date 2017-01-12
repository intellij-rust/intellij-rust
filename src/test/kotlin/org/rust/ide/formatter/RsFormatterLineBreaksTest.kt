package org.rust.ide.formatter

import com.intellij.psi.formatter.FormatterTestCase

class RsFormatterLineBreaksTest : FormatterTestCase() {
    override fun getTestDataPath() = "src/test/resources"

    override fun getBasePath() = "org/rust/ide/formatter/fixtures/line_breaks"

    override fun getFileExtension() = "rs"

    fun testAll() = doTest()
    fun testTraits() = doTest()

    fun testBlocks() = doTest()
    fun testBlocks2() = doTest()
}
