package org.rust.lang.core.parser;

public class RustCompleteParsingTestCase extends RustParsingTestCaseBase {

    public RustCompleteParsingTestCase() {
        super("parser/well-formed");
    }

    public void testFn()             { doTest(true); }
    public void testExpr()           { doTest(true); }
    public void testMod()            { doTest(true); }
    public void testUseItem()        { doTest(true); }
    public void testType()           { doTest(true); }
    public void testShifts()         { doTest(true); }
    public void testPatterns()       { doTest(true); }
    public void testAttributes()     { doTest(true); }
    public void testTraits()         { doTest(true); }
    public void testMacros()         { doTest(true); }
    public void testImpls()          { doTest(true); }
    public void testSuper()          { doTest(true); }
    public void testRanges()         { doTest(true); }
}
