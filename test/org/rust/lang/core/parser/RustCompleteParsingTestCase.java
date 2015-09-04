package org.rust.lang.core.parser;

public class RustCompleteParsingTestCase extends RustParsingTestCaseBase {

    @Override
    protected String getTestDataPath() {
        return "test/testData";
    }

    public RustCompleteParsingTestCase() {
        super("parser/well-formed");
    }

    public void testFn()    { doTest(true); }
    public void testMod()   { doTest(true); }

}
