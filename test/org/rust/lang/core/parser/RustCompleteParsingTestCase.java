package org.rust.lang.core.parser;

public class RustCompleteParsingTestCase extends RustParsingTestCaseBase {

    public RustCompleteParsingTestCase() {
        super("parser/well-formed");
    }

    public void testFn()        { doTest(true); }
    public void testExpr()      { doTest(true); }
    public void testMod()       { doTest(true); }
    public void testUseItem()   { doTest(true); }
    public void testType()      { doTest(true); }
    public void testGtgt()      { doTest(true); }
    public void testPatterns()  { doTest(true); }
}
