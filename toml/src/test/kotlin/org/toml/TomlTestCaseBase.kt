package org.toml

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

abstract class TomlTestCaseBase : LightCodeInsightFixtureTestCase() {

    final protected val fileName: String get() = "${camelToSnake(getTestName(true))}.toml"

    companion object {
        @JvmStatic
        fun camelToSnake(camelCaseName: String): String =
            camelCaseName.split("(?=[A-Z])".toRegex())
                .map { it.toLowerCase() }
                .joinToString("_")
    }
}
