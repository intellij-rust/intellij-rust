package org.rust.lang

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import com.intellij.util.containers.MultiMap
import org.rust.cargo.CargoProjectDescription
import org.rust.cargo.toolchain.CargoMetadataService
import org.rust.cargo.toolchain.impl.CargoMetadataServiceImpl
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

    open class RustProjectDescriptor : LightProjectDescriptor() {

        final override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
            super.configureModule(module, model, contentEntry)

            val moduleBaseDir = contentEntry.file!!.url
            val metadataService = module.getService<CargoMetadataService>() as CargoMetadataServiceImpl
            metadataService.state.cargoProjectDescription = testCargoProject(moduleBaseDir)
        }

        open protected fun testCargoProject(contentRoot: String): CargoProjectDescription {
            val packages = listOf(testCargoPackage(contentRoot))
            return CargoProjectDescription.create(packages, MultiMap())!!
        }

        protected fun testCargoPackage(contentRoot: String, name: String ="test-package") = CargoProjectDescription.Package(
            contentRoot,
            name = name,
            version = "0.0.1",
            targets = listOf(
                CargoProjectDescription.Target("$contentRoot/main.rs", CargoProjectDescription.TargetKind.BIN),
                CargoProjectDescription.Target("$contentRoot/lib.rs", CargoProjectDescription.TargetKind.LIB)
            ),
            source = null
        )
    }

    class WithStdlibRustProjectDescriptor : RustProjectDescriptor() {
        override fun getSdk(): Sdk? {
            // FIXME: attach sdk
            val sdkArchiveFile = LocalFileSystem.getInstance()
                .findFileByPath("${RustTestCase.testResourcesPath}/rustc-src.zip")

            val sdkFile = checkNotNull(
                sdkArchiveFile?.let { JarFileSystem.getInstance().getJarRootForLocalFile(it) },
                { "Rust sources archive not found. Run `./gradlew test` to download the archive." }
            )
            return null
        }
    }
}
