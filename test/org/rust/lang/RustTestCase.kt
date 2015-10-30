package org.rust.lang

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

abstract class RustTestCase : LightCodeInsightFixtureTestCase() {
    final protected val fileName: String
        get() = "${camelToSnake(getTestName(true))}.rs"

    final protected val goldFileName: String
        get() = "${camelToSnake(getTestName(true))}_gold.rs"

    private fun camelToSnake(camelCaseName: String): String =
            camelCaseName.split("(?=[A-Z])".toRegex())
                    .map { it.toLowerCase() }
                    .joinToString("_")

}
