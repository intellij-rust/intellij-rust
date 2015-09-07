package org.rust.lang.core.parser;

import com.intellij.testFramework.ParsingTestCase;
import org.jetbrains.annotations.NonNls;
import org.rust.lang.core.RustParserDefinition;

public abstract class RustParsingTestCaseBase extends ParsingTestCase {

    @Override
    protected String getTestDataPath() {
        return "test/testData";
    }

    @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
    public RustParsingTestCaseBase(@NonNls final String dataPath) {
        super("psi/" + dataPath, "rust", true /*lowerCaseFirstLetter*/, new RustParserDefinition());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

}
