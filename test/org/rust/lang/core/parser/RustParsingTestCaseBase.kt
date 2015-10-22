package org.rust.lang.core.parser

import com.intellij.testFramework.ParsingTestCase
import org.jetbrains.annotations.NonNls
import org.rust.lang.core.RustParserDefinition

abstract class RustParsingTestCaseBase(@NonNls dataPath: String)
 : ParsingTestCase("psi/" + dataPath, "rs", true /*lowerCaseFirstLetter*/, RustParserDefinition()) {

    override fun getTestDataPath(): String = "testData"

    override fun setUp() {
        super.setUp()
    }

    override fun tearDown() {
        super.tearDown()
    }
}
