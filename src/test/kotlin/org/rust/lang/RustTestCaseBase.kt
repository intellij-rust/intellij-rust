package org.rust.lang

import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
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
import org.intellij.lang.annotations.Language
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.project.workspace.impl.CargoProjectWorkspaceImpl
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Rustup
import org.rust.cargo.toolchain.impl.CleanCargoMetadata
import org.rust.cargo.util.StandardLibraryRoots
import org.rust.lang.core.psi.util.parentOfType
import java.util.*

abstract class RustTestCaseBase : LightPlatformCodeInsightFixtureTestCase(), RustTestCase {

    override fun getProjectDescriptor(): LightProjectDescriptor = DefaultDescriptor

    override fun isWriteActionRequired(): Boolean = false

    abstract val dataPath: String

    override fun getTestDataPath(): String = "${RustTestCase.testResourcesPath}/$dataPath"

    override fun runTest() {
        val projectDescriptor = projectDescriptor
        if (projectDescriptor is RustProjectDescriptorBase.WithRustup) {
            if (projectDescriptor.rustup == null) {
                System.err.println("SKIP $name: no rustup found")
                return
            }
            if (projectDescriptor.stdlib == null) {
                System.err.println("SKIP $name: rustup found, but there is no stdlib available")
                return
            }
        }

        super.runTest()
    }

    protected val fileName: String
        get() = "$testName.rs"

    protected val testName: String
        get() = camelToSnake(getTestName(true))

    protected fun checkByFile(ignoreTrailingWhitespace: Boolean = true, action: () -> Unit) {
        val (before, after) = (fileName to fileName.replace(".rs", "_after.rs"))
        myFixture.configureByFile(before)
        action()
        myFixture.checkResultByFile(after, ignoreTrailingWhitespace)
    }

    protected fun checkByDirectory(action: () -> Unit) {
        val (before, after) = ("$testName/before" to "$testName/after")

        val targetPath = ""
        val beforeDir = myFixture.copyDirectoryToProject(before, targetPath)

        action()

        val afterDir = getVirtualFileByName("$testDataPath/$after")
        PlatformTestUtil.assertDirectoriesEqual(afterDir, beforeDir)
    }

    protected fun checkByText(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        action: () -> Unit
    ) {
        InlineFile(before)
        action()
        myFixture.checkResult(replaceCaretMarker(after))
    }

    protected fun openFileInEditor(path: String) {
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir(path))
    }

    data class ProjectFile(val path: String, val text: String) {
        companion object {
            fun parseFileCollection(text: String): List<ProjectFile> {
                val fileSeparator = """^\s* //- (\S+)\s*$""".toRegex(RegexOption.MULTILINE)
                val fileNames = fileSeparator.findAll(text).map { it.groupValues[1] }.toList()
                val fileTexts = fileSeparator.split(text).filter(String::isNotBlank)

                check(fileNames.size == fileTexts.size) {
                    "Have you placed `//- filename.rs` markers?"
                }

                return fileNames.zip(fileTexts).map({ ProjectFile(it.first, it.second) })
            }
        }
    }

    protected fun getVirtualFileByName(path: String): VirtualFile? =
        LocalFileSystem.getInstance().findFileByPath(path)

    protected inline fun <reified X : Throwable> expect(f: () -> Unit) {
        try {
            f()
        } catch (e: Throwable) {
            if (e is X)
                return
            throw e
        }
        fail("No ${X::class.java} was thrown during the test")
    }

    inner class InlineFile(private @Language("Rust") val code: String, val name: String = "main.rs") {
        private val hasCaretMarker = "/*caret*/" in code

        init {
            myFixture.configureByText(name, replaceCaretMarker(code))
        }

        fun withCaret() {
            check(hasCaretMarker) {
                "Please, add `/*caret*/` marker to\n$code"
            }
        }
    }

    inline fun <reified T : PsiElement> findElementInEditor(marker: String = "^"): T {
        val (element, data) = findElementAndDataInEditor<T>(marker)
        check(data.isEmpty()) { "Did not expect marker data" }
        return element
    }

    inline fun <reified T : PsiElement> findElementAndDataInEditor(marker: String = "^"): Pair<T, String> {
        val caretMarker = "//$marker"
        val (elementAtMarker, data) = run {
            val text = myFixture.file.text
            val markerOffset = text.indexOf(caretMarker)
            check(markerOffset != -1) { "No `$marker` marker:\n$text" }

            val data = text.drop(markerOffset).removePrefix(caretMarker).takeWhile { it != '\n' }.trim()
            val markerPosition = myFixture.editor.offsetToLogicalPosition(markerOffset + caretMarker.length - 1)
            val previousLine = LogicalPosition(markerPosition.line - 1, markerPosition.column)
            val elementOffset = myFixture.editor.logicalPositionToOffset(previousLine)
            myFixture.file.findElementAt(elementOffset)!! to data
        }
        val element = elementAtMarker.parentOfType<T>(strict = false)
            ?: error("No ${T::class.java.simpleName} at ${elementAtMarker.text}")
        return element to data
    }

    protected fun replaceCaretMarker(text: String) = text.replace("/*caret*/", "<caret>")

    protected fun reportTeamCityMetric(name: String, value: Long) {
        //https://confluence.jetbrains.com/display/TCD10/Build+Script+Interaction+with+TeamCity#BuildScriptInteractionwithTeamCity-ReportingBuildStatistics
        if (UsefulTestCase.IS_UNDER_TEAMCITY) {
            println("##teamcity[buildStatisticValue key='$name' value='$value']")
        } else {
            println("$name: $value")
        }
    }

    protected fun applyQuickFix(name: String) {
        val action = myFixture.findSingleIntention(name)
        myFixture.launchAction(action)
    }

    protected open class RustProjectDescriptorBase : LightProjectDescriptor() {
        open class WithRustup : RustProjectDescriptorBase() {
            override fun setUpProject(project: Project, handler: SetupHandler) {
                if (WithStdlibRustProjectDescriptor.rustup == null) return
                // The actual tests should be skipped as well, if we skip the setup
                super.setUpProject(project, handler)
            }

            private val toolchain: RustToolchain? by lazy { RustToolchain.suggest() }

            val rustup by lazy { toolchain?.rustup("/") }
            val stdlib by lazy { (rustup?.downloadStdlib() as? Rustup.DownloadResult.Ok)?.library }
        }

        final override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
            super.configureModule(module, model, contentEntry)

            val moduleBaseDir = contentEntry.file!!.url
            val projectWorkspace = CargoProjectWorkspace.forModule(module) as CargoProjectWorkspaceImpl

            projectWorkspace.setState(testCargoProject(module, moduleBaseDir))

            // XXX: for whatever reason libraries created by `updateLibrary` are not indexed in tests.
            // this seems to fix the issue
            val libraries = LibraryTablesRegistrar.getInstance().getLibraryTable(module.project).libraries
            for (lib in libraries) {
                model.addLibraryEntry(lib)
            }
        }

        open protected fun testCargoProject(module: Module, contentRoot: String): CargoProjectDescription {
            val packages = listOf(testCargoPackage(contentRoot))
            return CargoProjectDescription.deserialize(CleanCargoMetadata(packages, ArrayList()))!!
        }

        protected fun testCargoPackage(contentRoot: String, name: String = "test-package") = CleanCargoMetadata.Package(
            contentRoot,
            name = name,
            version = "0.0.1",
            targets = listOf(
                CleanCargoMetadata.Target("$contentRoot/main.rs", name, CargoProjectDescription.TargetKind.BIN),
                CleanCargoMetadata.Target("$contentRoot/lib.rs", name, CargoProjectDescription.TargetKind.LIB)
            ),
            source = null,
            isWorkspaceMember = true
        )

        protected fun externalPackage(name: String, targetName: String = name) = CleanCargoMetadata.Package(
            "",
            name = name,
            version = "0.0.1",
            targets = listOf(
                CleanCargoMetadata.Target("", targetName, CargoProjectDescription.TargetKind.LIB)
            ),
            source = null,
            isWorkspaceMember = false
        )
    }

    protected object DefaultDescriptor : RustProjectDescriptorBase()

    protected object WithStdlibRustProjectDescriptor : RustProjectDescriptorBase.WithRustup() {
        override fun testCargoProject(module: Module, contentRoot: String): CargoProjectDescription {

            StandardLibraryRoots.fromFile(stdlib!!)!!.attachTo(module)

            val packages = listOf(testCargoPackage(contentRoot))

            return CleanCargoMetadata(packages, emptyList()).let {
                CargoProjectDescription.deserialize(it)!!
            }
        }
    }

    protected object WithStdlibAndDependencyRustProjectDescriptor : RustProjectDescriptorBase.WithRustup() {
        override fun testCargoProject(module: Module, contentRoot: String): CargoProjectDescription {

            StandardLibraryRoots.fromFile(stdlib!!)!!.attachTo(module)

            val packages = listOf(
                testCargoPackage(contentRoot),
                externalPackage("dep-lib", "dep-lib-target"),
                externalPackage("trans-lib"))

            val depNodes = ArrayList<CleanCargoMetadata.DependencyNode>()
            depNodes.add(CleanCargoMetadata.DependencyNode(0, listOf(1)))   // Our package depends on test_dep

            return CleanCargoMetadata(packages, depNodes).let {
                CargoProjectDescription.deserialize(it)!!
            }
        }
    }

    companion object {
        @JvmStatic
        fun camelToSnake(camelCaseName: String): String =
            camelCaseName.split("(?=[A-Z])".toRegex())
                .map(String::toLowerCase)
                .joinToString("_")
    }
}

