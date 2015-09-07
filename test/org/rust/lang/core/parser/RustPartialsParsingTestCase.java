package org.rust.lang.core.parser;

public class RustPartialsParsingTestCase extends RustParsingTestCaseBase {

    public RustPartialsParsingTestCase() {
        super("parser/ill-formed");
    }

    public void testFn() { doTest(true); }

}
