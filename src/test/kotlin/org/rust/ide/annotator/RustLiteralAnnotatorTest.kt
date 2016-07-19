package org.rust.ide.annotator

class RustLiteralAnnotatorTest : RustAnnotatorTestBase() {
    override val dataPath = "org/rust/ide/annotator/fixtures/literals"

    fun testCharLiteralLength() = doTest()
    fun testLiteralSuffixes() = doTest()
    fun testLiteralUnclosedQuotes() = doTest()
}

