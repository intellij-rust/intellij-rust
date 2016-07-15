package org.rust.lang

import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.project.CargoProjectDescriptionData
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.project.workspace.impl.CargoProjectWorkspaceImpl
import org.rust.cargo.util.StandardLibraryRoots
import org.rust.cargo.util.getComponentOrThrow
import java.util.*

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

    protected fun configureAndFindElement(code: String): Pair<PsiElement, String> {
        val caretMarker = "//^"
        val markerOffset = code.indexOf(caretMarker)
        val data = code.drop(markerOffset).removePrefix(caretMarker).takeWhile { it != '\n' }.trim()
        check(markerOffset != -1)
        myFixture.configureByText("main.rs", code)
        val markerPosition = myFixture.editor.offsetToLogicalPosition(markerOffset + caretMarker.length - 1)
        val previousLine = LogicalPosition(markerPosition.line - 1, markerPosition.column)
        val elementOffset = myFixture.editor.logicalPositionToOffset(previousLine)
        return myFixture.file.findElementAt(elementOffset)!! to data
    }

    protected fun reportTeamCityMetric(name: String, value: Long) {
        //https://confluence.jetbrains.com/display/TCD10/Build+Script+Interaction+with+TeamCity#BuildScriptInteractionwithTeamCity-ReportingBuildStatistics
        if (UsefulTestCase.IS_UNDER_TEAMCITY) {
            println("##teamcity[buildStatisticValue key='$name' value='$value']")
        }
    }

    protected fun applyQuickFix(name: String) {
        val action = myFixture.findSingleIntention(name)
        myFixture.launchAction(action)
    }

    open class RustProjectDescriptor : LightProjectDescriptor() {

        final override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
            super.configureModule(module, model, contentEntry)

            val moduleBaseDir = contentEntry.file!!.url
            val metadataService = module.getComponentOrThrow<CargoProjectWorkspace>() as CargoProjectWorkspaceImpl

            metadataService.setState(testCargoProject(module, moduleBaseDir))

            // XXX: for whatever reason libraries created by `updateLibrary` are not indexed in tests.
            // this seems to fix the issue
            val libraries = LibraryTablesRegistrar.getInstance().getLibraryTable(module.project).libraries
            for (lib in libraries) {
                model.addLibraryEntry(lib)
            }
        }

        open protected fun testCargoProject(module: Module, contentRoot: String): CargoProjectDescription {
            val packages = listOf(testCargoPackage(contentRoot))
            return CargoProjectDescription.deserialize(CargoProjectDescriptionData(0, packages, ArrayList()))!!
        }

        protected fun testCargoPackage(contentRoot: String, name: String = "test-package") = CargoProjectDescriptionData.Package(
            contentRoot,
            name = name,
            version = "0.0.1",
            targets = listOf(
                CargoProjectDescriptionData.Target("$contentRoot/main.rs", name, CargoProjectDescription.TargetKind.BIN),
                CargoProjectDescriptionData.Target("$contentRoot/lib.rs", name, CargoProjectDescription.TargetKind.LIB)
            ),
            source = null
        )

    }

    class WithStdlibRustProjectDescriptor : RustProjectDescriptor() {

        override fun testCargoProject(module: Module, contentRoot: String): CargoProjectDescription {
            checkNotNull(StandardLibraryRoots.fromFile(rustSourcesArchive())) {
                "Corrupted invalid Rust sources"
            }.attachTo(module)

            val packages = listOf(testCargoPackage(contentRoot))

            return CargoProjectDescriptionData(0, packages, emptyList()).let {
                CargoProjectDescription.deserialize(it)!!
            }
        }
    }

    companion object {
        @JvmStatic
        fun camelToSnake(camelCaseName: String): String =
            camelCaseName.split("(?=[A-Z])".toRegex())
                .map { it.toLowerCase() }
                .joinToString("_")

        @JvmStatic
        fun rustSourcesArchive(): VirtualFile =
            checkNotNull(LocalFileSystem.getInstance()
                .findFileByPath("${RustTestCase.testResourcesPath}/rustc-src.zip")) {
                "Rust sources archive not found. Run `./gradlew test` to download the archive."
            }
    }
}

