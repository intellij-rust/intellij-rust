package org.toml.lang.core.parser

class TomlCompleteParsingTestCase : TomlParsingTestCaseBase("complete") {
    fun testExample() = doTest(true)
    fun testValues() = doTest(true)
}
