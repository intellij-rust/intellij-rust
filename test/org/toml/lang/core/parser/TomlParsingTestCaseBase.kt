package org.toml.lang.core.parser

import com.intellij.testFramework.ParsingTestCase
import org.jetbrains.annotations.NonNls

abstract class TomlParsingTestCaseBase(@NonNls dataPath: String)
: ParsingTestCase("psi/" + dataPath, "toml", true /*lowerCaseFirstLetter*/, TomlParserDefinition()) {
    override fun getTestDataPath() = "testData/toml"
}
