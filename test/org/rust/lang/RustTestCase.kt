package org.rust.lang

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

abstract class RustTestCase : LightCodeInsightFixtureTestCase() {
    final protected val fileName: String
        get() = getTestName(true) + ".rs"
}