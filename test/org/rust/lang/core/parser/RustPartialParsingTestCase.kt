package org.rust.lang.core.parser

class RustPartialParsingTestCase : RustParsingTestCaseBase("parser/ill-formed") {

    // @formatter:off
    fun testFn()                    { doTest(true) }
    fun testUseItem()               { doTest(true) }
    // @formatter:on
}
