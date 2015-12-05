package org.toml.lang

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.rust.lang.RustTestCase

abstract class TomlTestCase : LightCodeInsightFixtureTestCase() {
    final protected val fileName: String get() = "${RustTestCase.camelToSnake(getTestName(true))}.toml"
}
