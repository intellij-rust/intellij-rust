package org.rust.lang

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase

abstract class RustTestCaseBase : LightPlatformCodeInsightFixtureTestCase(), RustTestCase {

    override fun isWriteActionRequired(): Boolean = false

    abstract val dataPath: String

    override fun getTestDataPath(): String = "${RustTestCase.testResourcesPath}/$dataPath"

    protected val fileName: String
        get() = "$testName.rs"

    protected val testName: String
        get() = camelToSnake(getTestName(true))

    protected fun checkByFile(ignoreTrailingWhitespace: Boolean = true, action: () -> Unit) {
        val before = fileName
        val after = before.replace(".rs", "_after.rs")
        myFixture.configureByFile(before)
        action()
        myFixture.checkResultByFile(after, ignoreTrailingWhitespace)
    }

    protected fun checkByDirectory(action: () -> Unit) {
        val after = "$testName/after"
        val before = "$testName/before"

        val targetPath = ""
        val beforeDir = myFixture.copyDirectoryToProject(before, targetPath)

        action()

        val afterDir = getVirtualFileByName("$testDataPath/$after")
        PlatformTestUtil.assertDirectoriesEqual(afterDir, beforeDir)
    }

    protected fun openFileInEditor(path: String) {
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir(path))
    }

    protected fun getVirtualFileByName(path: String): VirtualFile? =
        LocalFileSystem.getInstance().findFileByPath(path)

    companion object {
        @JvmStatic
        fun camelToSnake(camelCaseName: String): String =
                camelCaseName.split("(?=[A-Z])".toRegex())
                        .map { it.toLowerCase() }
                        .joinToString("_")
    }
}
