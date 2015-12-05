package org.toml.lang.core.parser

import com.intellij.testFramework.ParsingTestCase
import org.jetbrains.annotations.NonNls

abstract class TomlParsingTestCaseBase(@NonNls dataPath: String)
: ParsingTestCase("org/toml/lang/core/parser/fixtures/" + dataPath, "toml", true /*lowerCaseFirstLetter*/, TomlParserDefinition()) {
    override fun getTestDataPath() = "src/test/resources"
}
