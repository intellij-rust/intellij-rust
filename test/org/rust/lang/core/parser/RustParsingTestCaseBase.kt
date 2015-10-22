package org.rust.lang.core.parser

import com.intellij.lang.LanguageBraceMatching
import com.intellij.testFramework.ParsingTestCase
import org.jetbrains.annotations.NonNls
import org.rust.lang.RustLanguage
import org.rust.lang.core.RustParserDefinition
import org.rust.lang.highlight.RustBraceMatcher

abstract class RustParsingTestCaseBase(@NonNls dataPath: String)
 : ParsingTestCase("psi/" + dataPath, "rs", true /*lowerCaseFirstLetter*/, RustParserDefinition()) {

    override fun getTestDataPath(): String = "testData"

    override fun setUp() {
        super.setUp()
        addExplicitExtension(LanguageBraceMatching.INSTANCE, RustLanguage.INSTANCE, RustBraceMatcher())
    }

    override fun tearDown() {
        super.tearDown()
    }
}
