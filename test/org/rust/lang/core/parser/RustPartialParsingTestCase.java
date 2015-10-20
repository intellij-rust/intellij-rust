package org.rust.lang.core.parser;

public class RustPartialParsingTestCase extends RustParsingTestCaseBase {

    public RustPartialParsingTestCase() {
        super("parser/ill-formed");
    }

    public void testFn()        { doTest(true); }
    public void testUseItem()   { doTest(true); }

}
