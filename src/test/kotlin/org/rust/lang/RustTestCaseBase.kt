package org.rust.lang

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import org.rust.cargo.CargoProjectDescription
import org.rust.cargo.project.RustSdkType
import org.rust.cargo.project.module.RustModuleType
import org.rust.cargo.project.module.persistence.CargoModuleService
import org.rust.cargo.project.module.persistence.ExternCrateData
import org.rust.cargo.util.getService

abstract class RustTestCaseBase : LightPlatformCodeInsightFixtureTestCase(), RustTestCase {

    override fun getProjectDescriptor(): LightProjectDescriptor = RustProjectDescriptor()

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

    open class RustProjectDescriptor : DefaultLightProjectDescriptor() {
        override fun getModuleType(): ModuleType<*> = RustModuleType.INSTANCE

        override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
            super.configureModule(module, model, contentEntry)
            module.getService<CargoModuleService>().saveData(targets, externCrates)
        }

        open protected val externCrates: List<ExternCrateData> = emptyList()

        private val targets: List<CargoProjectDescription.Target> = listOf(
            CargoProjectDescription.Target("main.rs", CargoProjectDescription.TargetKind.BIN),
            CargoProjectDescription.Target("lib.rs", CargoProjectDescription.TargetKind.LIB)
        )
    }

    class WithSdkRustProjectDescriptor : RustProjectDescriptor() {
        override fun getSdk(): Sdk? {
            val sdk = ProjectJdkImpl("RustTest", RustSdkType.INSTANCE)
            val sdkModificator = sdk.sdkModificator

            val sdkSrc = "${RustTestCase.testResourcesPath}/rustc-nightly/src"
            val sdkSrcFile = LocalFileSystem.getInstance().findFileByPath(sdkSrc)
            sdkModificator.addRoot(sdkSrcFile, OrderRootType.CLASSES)
            sdkModificator.commitChanges()
            return sdk
        }
    }
}
