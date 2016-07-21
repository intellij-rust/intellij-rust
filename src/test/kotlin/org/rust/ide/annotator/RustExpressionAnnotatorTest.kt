package org.rust.ide.annotator

class RustExpressionAnnotatorTest : RustAnnotatorTestBase() {
    override val dataPath = "org/rust/ide/annotator/fixtures/expressions"

    fun testUnnecessaryIfParens() = doTest()
    fun testUnnecessaryForParens() = doTest()
    fun testUnnecessaryMatchParens() = doTest()
    fun testUnnecessaryReturnParens() = doTest()
    fun testUnnecessaryWhileParens() = doTest()
    fun testRedundantParens() = doTest()


    fun testStructExpr() = doTest()
    fun testStructExprQuickFix() = checkQuickFix("Add missing fields")
    fun testStructExprQuickFix2() = checkQuickFix("Add missing fields")
}
