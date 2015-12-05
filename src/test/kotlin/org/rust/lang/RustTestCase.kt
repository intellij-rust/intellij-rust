package org.rust.lang

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

abstract class RustTestCase : LightCodeInsightFixtureTestCase() {

    final protected val fileName: String
        get() = "$testName.rs"

    final protected val testName: String
        get() = camelToSnake(getTestName(true))


    companion object {
        @JvmStatic
        fun camelToSnake(camelCaseName: String): String =
                camelCaseName.split("(?=[A-Z])".toRegex())
                        .map { it.toLowerCase() }
                        .joinToString("_")
    }
}
