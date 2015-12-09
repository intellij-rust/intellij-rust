package org.rust.lang.formatter

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.formatter.FormatterTestCase
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class RustFormatterTestCase(val fileName: String) : FormatterTestCase() {

    override fun getTestDataPath() = TEST_DATA_PATH
    override fun getBasePath() = BASE_PATH
    override fun getFileExtension() = "rs"

    override fun getTestName(lowercaseFirstLetter: Boolean) = fileName

    @Before
    fun before() = invokeTestRunnable { setUp() }

    @Test
    fun test() = WriteCommandAction.runWriteCommandAction(LightPlatformTestCase.getProject(), {
        doTest(fileName, fileName + "_after")
    })

    @After
    fun after() = invokeTestRunnable { tearDown() }

    companion object {
        val TEST_DATA_PATH = "src/test/resources"
        val BASE_PATH = "org/rust/lang/formatter/fixtures"

        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun params() = File(FileUtil.toSystemDependentName("$TEST_DATA_PATH/$BASE_PATH"))
            .listFiles { !it.nameWithoutExtension.endsWith("_after") }!!
            .map { arrayOf(it.nameWithoutExtension) }
    }
}
