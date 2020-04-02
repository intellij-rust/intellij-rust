/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.TestCase
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.LanguageCommenters
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapiext.Testmark
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.text.SemVer
import junit.framework.AssertionFailedError
import org.intellij.lang.annotations.Language
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.model.impl.testCargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.RustcVersion
import org.rust.lang.core.macros.findExpansionElements
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.ext.startOffset
import org.rust.openapiext.saveAllDocuments
import org.rust.stdext.BothEditions
import kotlin.reflect.KMutableProperty0

abstract class RsTestBase : BasePlatformTestCase(), RsTestCase {

    override fun getProjectDescriptor(): LightProjectDescriptor {
        val annotation = findAnnotationInstance<ProjectDescriptor>() ?: return DefaultDescriptor
        return annotation.descriptor.objectInstance
            ?: error("Only Kotlin objects defined with `object` keyword are allowed")
    }

    /** Tries to find the specified annotation on the current test method and then on the current class */
    private inline fun <reified T : Annotation> findAnnotationInstance(): T? =
        javaClass.getMethod(name).getAnnotation(T::class.java) ?: javaClass.getAnnotation(T::class.java)

    override fun isWriteActionRequired(): Boolean = false

    open val dataPath: String = ""

    override fun getTestDataPath(): String = "${TestCase.testResourcesPath}/$dataPath"

    override fun setUp() {
        super.setUp()

        (projectDescriptor as? RustProjectDescriptorBase)?.setUp(myFixture)

        setupMockRustcVersion()
        setupMockEdition()
        setupMockCfgOptions()
        findAnnotationInstance<ExpandMacros>()?.let {
            val disposable = project.macroExpansionManager.setUnitTestExpansionModeAndDirectory(it.mode, it.cache)
            Disposer.register(testRootDisposable, disposable)
        }
        disableMissedCacheAssertions(testRootDisposable)
    }

    override fun tearDown() {
        super.tearDown()
        checkMacroExpansionFileSystemAfterTest()
    }

    private fun setupMockRustcVersion() {
        val annotation = findAnnotationInstance<MockRustcVersion>() ?: return
        val (semVer, channel) = parse(annotation.rustcVersion)
        val rustcInfo = RustcInfo("", RustcVersion(semVer, "", channel))
        project.testCargoProjects.setRustcInfo(rustcInfo, testRootDisposable)
    }

    private fun setupMockEdition() {
        val edition = findAnnotationInstance<MockEdition>()?.edition ?: CargoWorkspace.Edition.EDITION_2015
        project.testCargoProjects.setEdition(edition, testRootDisposable)
    }

    private fun setupMockCfgOptions() {
        val additionalOptions = findAnnotationInstance<MockAdditionalCfgOptions>()?.options
            ?.takeIf { it.isNotEmpty() } ?: return

        val allOptions = CfgOptions(
            CfgOptions.DEFAULT.keyValueOptions,
            CfgOptions.DEFAULT.nameOptions + additionalOptions
        )
        project.testCargoProjects.setCfgOptions(allOptions)
    }

    private fun parse(version: String): Pair<SemVer, RustChannel> {
        val versionRe = """(\d+\.\d+\.\d+)(.*)""".toRegex()
        val result = versionRe.matchEntire(version) ?: error("$version should match `${versionRe.pattern}` pattern")

        val versionText = result.groups[1]?.value ?: error("")
        val semVer = SemVer.parseFromText(versionText) ?: error("")

        val releaseSuffix = result.groups[2]?.value.orEmpty()
        val channel = when {
            releaseSuffix.isEmpty() -> RustChannel.STABLE
            releaseSuffix.startsWith("-beta") -> RustChannel.BETA
            releaseSuffix.startsWith("-nightly") -> RustChannel.NIGHTLY
            else -> RustChannel.DEFAULT
        }
        return semVer to channel
    }

    override fun runTest() {
        val reason = skipTestReason
        if (reason != null) {
            System.err.println("SKIP \"$name\": $reason")
            return
        }

        if (findAnnotationInstance<BothEditions>() != null) {
            if (findAnnotationInstance<MockEdition>() != null) {
                error("Can't mix `BothEditions` and `MockEdition` annotations")
            }
            // These functions exist to simplify stacktrace analyzing
            runTestEdition2015()
            runTestEdition2018()
        } else {
            super.runTest()
        }
    }

    protected open val skipTestReason: String?
        get() {
            val projectDescriptor = projectDescriptor as? RustProjectDescriptorBase
            var reason = projectDescriptor?.skipTestReason
            if (reason == null) {
                val minRustVersion = findAnnotationInstance<MinRustcVersion>() ?: return null
                val requiredVersion = minRustVersion.semver
                val rustcVersion = projectDescriptor?.rustcInfo?.version ?: return null
                if (rustcVersion.semver < requiredVersion) {
                    reason = "$requiredVersion Rust version required, ${rustcVersion.semver} found"
                }
            }
            return reason
        }

    private fun runTestEdition2015() {
        project.testCargoProjects.setEdition(CargoWorkspace.Edition.EDITION_2015, testRootDisposable)
        super.runTest()
    }

    private fun runTestEdition2018() {
        project.testCargoProjects.setEdition(CargoWorkspace.Edition.EDITION_2018, testRootDisposable)
        super.runTest()
    }

    protected val fileName: String
        get() = "$testName.rs"

    private val testName: String
        get() = getTestName(true)

    override fun getTestName(lowercaseFirstLetter: Boolean): String {
        val camelCase = super.getTestName(lowercaseFirstLetter)
        return TestCase.camelOrWordsToSnake(camelCase)
    }

    protected fun checkByFile(ignoreTrailingWhitespace: Boolean = true, action: () -> Unit) {
        val (before, after) = (fileName to fileName.replace(".rs", "_after.rs"))
        myFixture.configureByFile(before)
        action()
        myFixture.checkResultByFile(after, ignoreTrailingWhitespace)
    }

    protected fun checkByDirectory(action: (VirtualFile) -> Unit) {
        val (before, after) = ("$testName/before" to "$testName/after")

        val targetPath = ""
        val beforeDir = myFixture.copyDirectoryToProject(before, targetPath)

        action(beforeDir)

        val afterDir = getVirtualFileByName("$testDataPath/$after")
        PlatformTestUtil.assertDirectoriesEqual(afterDir, beforeDir)
    }

    protected fun checkByDirectory(@Language("Rust") before: String, @Language("Rust") after: String, action: (TestProject) -> Unit) {
        val testProject = fileTreeFromText(before).create()
        action(testProject)
        saveAllDocuments()
        fileTreeFromText(after).assertEquals(myFixture.findFileInTempDir("."))
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

    protected fun checkEditorAction(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        actionId: String,
        trimIndent: Boolean = true,
        testmark: Testmark? = null
    ) {
        fun String.trimIndentIfNeeded(): String = if (trimIndent) trimIndent() else this

        val action = {
            checkByText(before.trimIndentIfNeeded(), after.trimIndentIfNeeded()) {
                myFixture.performEditorAction(actionId)
            }
        }
        testmark?.checkHit { action() } ?: action()
    }

    protected fun openFileInEditor(path: String) {
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir(path))
    }

    private fun getVirtualFileByName(path: String): VirtualFile? =
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

    @Suppress("TestFunctionName")
    protected fun InlineFile(@Language("Rust") code: String, name: String = "main.rs"): InlineFile {
        return InlineFile(myFixture, code, name)
    }

    protected inline fun <reified T : PsiElement> findElementInEditor(marker: String = "^"): T =
        findElementInEditor(T::class.java, marker)

    protected fun <T : PsiElement> findElementInEditor(psiClass: Class<T>, marker: String): T {
        val (element, data) = findElementWithDataAndOffsetInEditor(psiClass, marker)
        check(data.isEmpty()) { "Did not expect marker data" }
        return element
    }

    protected inline fun <reified T : PsiElement> findElementAndDataInEditor(marker: String = "^"): Pair<T, String> {
        val (element, data) = findElementWithDataAndOffsetInEditor<T>(marker)
        return element to data
    }

    protected inline fun <reified T : PsiElement> findElementWithDataAndOffsetInEditor(
        marker: String = "^"
    ): Triple<T, String, Int> {
        return findElementWithDataAndOffsetInEditor(T::class.java, marker)
    }

    protected fun <T : PsiElement> findElementWithDataAndOffsetInEditor(
        psiClass: Class<T>,
        marker: String
    ): Triple<T, String, Int> {
        val elementsWithDataAndOffset = findElementsWithDataAndOffsetInEditor(psiClass, marker)
        check(elementsWithDataAndOffset.isNotEmpty()) { "No `$marker` marker:\n${myFixture.file.text}" }
        check(elementsWithDataAndOffset.size <= 1) { "More than one `$marker` marker:\n${myFixture.file.text}" }
        return elementsWithDataAndOffset.first()
    }

    protected inline fun <reified T : PsiElement> findElementsWithDataAndOffsetInEditor(
        marker: String = "^"
    ): List<Triple<T, String, Int>> {
        return findElementsWithDataAndOffsetInEditor(T::class.java, marker)
    }

    protected fun <T : PsiElement> findElementsWithDataAndOffsetInEditor(
        psiClass: Class<T>,
        marker: String
    ): List<Triple<T, String, Int>> {
        val commentPrefix = LanguageCommenters.INSTANCE.forLanguage(myFixture.file.language).lineCommentPrefix ?: "//"
        val caretMarker = "$commentPrefix$marker"
        val text = myFixture.file.text
        val result = mutableListOf<Triple<T, String, Int>>()
        var markerOffset = -caretMarker.length
        while (true) {
            markerOffset = text.indexOf(caretMarker, markerOffset + caretMarker.length)
            if (markerOffset == -1) break
            val data = text.drop(markerOffset).removePrefix(caretMarker).takeWhile { it != '\n' }.trim()
            val markerPosition = myFixture.editor.offsetToLogicalPosition(markerOffset + caretMarker.length - 1)
            val previousLine = LogicalPosition(markerPosition.line - 1, markerPosition.column)
            val elementOffset = myFixture.editor.logicalPositionToOffset(previousLine)
            val elementAtMarker = myFixture.file.findElementAt(elementOffset)!!

            if (followMacroExpansions) {
                val expandedElementAtMarker = elementAtMarker.findExpansionElements()?.singleOrNull()
                val expandedElement = expandedElementAtMarker?.let { PsiTreeUtil.getParentOfType(it, psiClass, false) }
                if (expandedElement != null) {
                    val offset = expandedElementAtMarker.startOffset + (elementOffset - elementAtMarker.startOffset)
                    result.add(Triple(expandedElement, data, offset))
                    continue
                }
            }

            val element = PsiTreeUtil.getParentOfType(elementAtMarker, psiClass, false)
            if (element != null) {
                result.add(Triple(element, data, elementOffset))
            } else {
                val injectionElement = InjectedLanguageManager.getInstance(project)
                    .findInjectedElementAt(myFixture.file, elementOffset)
                    ?.let { PsiTreeUtil.getParentOfType(it, psiClass, false) }
                    ?: error("No ${psiClass.simpleName} at ${elementAtMarker.text}")
                val injectionOffset = (injectionElement.containingFile.virtualFile as VirtualFileWindow)
                    .documentWindow.hostToInjected(elementOffset)
                result.add(Triple(injectionElement, data, injectionOffset))
            }
        }
        return result
    }

    protected open val followMacroExpansions: Boolean get() = false

    protected fun replaceCaretMarker(text: String) = text.replace("/*caret*/", "<caret>")

    protected fun reportTeamCityMetric(name: String, value: Long) {
        //https://confluence.jetbrains.com/display/TCD10/Build+Script+Interaction+with+TeamCity#BuildScriptInteractionwithTeamCity-ReportingBuildStatistics
        if (UsefulTestCase.IS_UNDER_TEAMCITY) {
            println("##teamcity[buildStatisticValue key='$name' value='$value']")
        } else {
            println("$name: $value")
        }
    }

    protected fun checkAstNotLoaded(fileFilter: VirtualFileFilter) {
        PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(fileFilter, testRootDisposable)
    }

    protected fun checkAstNotLoaded() {
        PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(VirtualFileFilter.ALL, testRootDisposable)
    }

    protected open fun configureByText(text: String) {
        InlineFile(text.trimIndent())
    }

    protected open fun configureByFileTree(text: String): TestProject {
        return fileTreeFromText(text).createAndOpenFileWithCaretMarker()
    }

    protected inline fun <T> withOptionValue(optionProperty: KMutableProperty0<T>, value: T, action: () -> Unit) {
        val oldValue = optionProperty.get()
        optionProperty.set(value)
        try {
            action()
        } finally {
            optionProperty.set(oldValue)
        }
    }

    companion object {
        // XXX: hides `Assert.fail`
        fun fail(message: String): Nothing {
            throw AssertionFailedError(message)
        }

        @JvmStatic
        fun checkHtmlStyle(html: String) {
            // http://stackoverflow.com/a/1732454
            val re = "<body>(.*)</body>".toRegex(RegexOption.DOT_MATCHES_ALL)
            val body = (re.find(html)?.let { it.groups[1]!!.value } ?: html).trim()
            check(body[0].isUpperCase()) {
                "Please start description with the capital latter"
            }

            check(body.last() == '.') {
                "Please end description with a period"
            }
        }

        @JvmStatic
        fun getResourceAsString(path: String): String? {
            val stream = RsTestBase::class.java.classLoader.getResourceAsStream(path)
                ?: return null

            return StreamUtil.readText(stream, Charsets.UTF_8)
        }
    }

    protected fun FileTree.create(): TestProject = create(myFixture)
    protected fun FileTree.createAndOpenFileWithCaretMarker(): TestProject = createAndOpenFileWithCaretMarker(myFixture)

    protected val PsiElement.lineNumber: Int
        get() = myFixture.getDocument(myFixture.file).getLineNumber(textOffset)
}
