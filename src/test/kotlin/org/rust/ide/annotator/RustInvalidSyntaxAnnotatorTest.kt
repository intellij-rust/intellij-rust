package org.rust.ide.annotator

class RustInvalidSyntaxAnnotatorTest : RustAnnotatorTestBase() {
    override val dataPath = "org/rust/ide/annotator/fixtures/syntax"

    fun testPaths() = doTest()
    fun testInvalidPub() = doTest()
}
