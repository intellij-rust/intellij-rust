package org.rust.lang.core.parser;

public class RustCompleteParsingTestCase extends RustParsingTestCaseBase {

    public RustCompleteParsingTestCase() {
        super("parser/well-formed");
    }

    public void testFn()    { doTest(true); }
    public void testExpr()  { doTest(true); }
    public void testMod()   { doTest(true); }

}
