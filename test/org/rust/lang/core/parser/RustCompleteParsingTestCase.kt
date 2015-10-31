package org.rust.lang.core.parser

class RustCompleteParsingTestCase : RustParsingTestCaseBase("parser/well-formed") {


    // @formatter:off
    fun testFn()                    { doTest(true) }
    fun testExpr()                  { doTest(true) }
    fun testMod()                   { doTest(true) }
    fun testUseItem()               { doTest(true) }
    fun testType()                  { doTest(true) }
    fun testShifts()                { doTest(true) }
    fun testPatterns()              { doTest(true) }
    fun testAttributes()            { doTest(true) }
    fun testTraits()                { doTest(true) }
    fun testMacros()                { doTest(true) }
    fun testImpls()                 { doTest(true) }
    fun testSuper()                 { doTest(true) }
    fun testRanges()                { doTest(true) }
    fun testExternCrates()          { doTest(true) }
    // @formatter:off
}
