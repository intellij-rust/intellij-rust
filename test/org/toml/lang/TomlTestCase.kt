package org.toml.lang

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

abstract class TomlTestCase : LightCodeInsightFixtureTestCase() {
    final protected val fileName: String get() = "${camelToSnake(getTestName(true))}.toml"
}
