package org.toml

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.rust.lang.RustTestCaseBase

abstract class TomlTestCase : LightCodeInsightFixtureTestCase() {
    final protected val fileName: String get() = "${RustTestCaseBase.camelToSnake(getTestName(true))}.toml"
}
