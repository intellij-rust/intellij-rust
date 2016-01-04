package org.rust.lang

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

abstract class RustTestCaseBase : LightCodeInsightFixtureTestCase(), RustTestCase {

    abstract val dataPath: String

    override fun getTestDataPath(): String = "${RustTestCase.testResourcesPath}/$dataPath"

    final protected val fileName: String
        get() = "$testName.rs"

    final protected val testName: String
        get() = camelToSnake(getTestName(true))

    final protected fun checkByFile(ignore_trailing_whitespace: Boolean = true, action: () -> Unit) {
        val before = fileName
        val after = before.replace(".rs", "_after.rs")
        myFixture.configureByFile(before)
        action()
        myFixture.checkResultByFile(after, ignore_trailing_whitespace)
    }


    companion object {
        @JvmStatic
        fun camelToSnake(camelCaseName: String): String =
                camelCaseName.split("(?=[A-Z])".toRegex())
                        .map { it.toLowerCase() }
                        .joinToString("_")
    }
}
