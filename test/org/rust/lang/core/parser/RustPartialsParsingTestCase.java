package org.rust.lang.core.parser;

public class RustPartialsParsingTestCase extends RustParsingTestCaseBase {

    @Override
    protected String getTestDataPath() {
        return "test/testData";
    }

    public RustPartialsParsingTestCase() {
        super("parser/ill-formed");
    }

    public void testFn() { doTest(true); }

}
