package org.rust.lang

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase

abstract class RustTestCaseBase : LightPlatformCodeInsightFixtureTestCase(), RustTestCase {

    override fun isWriteActionRequired(): Boolean = false

    abstract val dataPath: String

    override fun getTestDataPath(): String = "${RustTestCase.testResourcesPath}/$dataPath"

    final protected val fileName: String
        get() = "$testName.rs"

    final protected val testName: String
        get() = camelToSnake(getTestName(true))

    final protected fun checkByFile(ignoreTrailingWhitespace: Boolean = true, action: () -> Unit) {
        val before = fileName
        val after = before.replace(".rs", "_after.rs")
        myFixture.configureByFile(before)
        action()
        myFixture.checkResultByFile(after, ignoreTrailingWhitespace)
    }


    companion object {
        @JvmStatic
        fun camelToSnake(camelCaseName: String): String =
                camelCaseName.split("(?=[A-Z])".toRegex())
                        .map { it.toLowerCase() }
                        .joinToString("_")
    }
}
