/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.testFramework.*
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ThrowableRunnable
import com.intellij.util.text.SemVer
import junit.framework.AssertionFailedError
import org.intellij.lang.annotations.Language
import org.junit.runner.RunWith
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.impl.DEFAULT_EDITION_FOR_TESTS
import org.rust.cargo.project.model.impl.testCargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.cargo.project.workspace.PackageFeature
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.impl.RustcVersion
import org.rust.cargo.util.parseSemVer
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.settings.ExcludedPath
import org.rust.ide.settings.ExclusionType
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.openapiext.document
import org.rust.openapiext.saveAllDocuments
import org.rust.stdext.BothEditions
import org.rust.stdext.RsResult
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.full.createInstance

@RunWith(RsJUnit4TestRunner::class)
abstract class RsTestBase : BasePlatformTestCase(), RsTestCase {

    // Needed for assertion that the directory doesn't accidentally renamed during the test
    private var tempDirRootUrl: String? = null
    private var tempDirRoot: VirtualFile? = null

    override fun getProjectDescriptor(): LightProjectDescriptor = RustLightProjectDescriptor

    open val testWrapping: TestWrapping get() = TestWrapping.NONE
    private var shouldSkipTestWithCurrentWrapping: Boolean = false
    protected var testWrappingUnwrapper: TestUnwrapper? = null

    override fun isWriteActionRequired(): Boolean = false

    open val dataPath: String = ""

    override fun getTestDataPath(): String = "${TestCase.testResourcesPath}/$dataPath"

    override fun setUp() {
        super.setUp()

        invokeAndWaitIfNeeded { setupRustProject() }
        setupMockRustcVersion()
        setupMockEdition()
        setupMockCfgOptions()
        setupMockCargoFeatures()
        setupExperimentalFeatures()
        setupInspections()
        setupExcludedPaths()
        if (findAnnotationInstance<WithDisabledMacroExpansion>() != null) {
            val mgr = project.macroExpansionManager
            mgr.setMacroExpansionEnabled(false)
            Disposer.register(testRootDisposable) {
                mgr.setMacroExpansionEnabled(true)
            }
        }
        RecursionManager.disableMissedCacheAssertions(testRootDisposable)
        tempDirRoot = myFixture.findFileInTempDir(".")
        tempDirRootUrl = tempDirRoot?.url
    }

    override fun tearDown() {
        val oldTempDirRootUrl = tempDirRootUrl
        val newTempDirRootUrl = tempDirRoot?.url

        com.intellij.testFramework.common.runAll(
            {
                // Fixes flaky tests
                (ProjectLevelVcsManagerEx.getInstance(project) as ProjectLevelVcsManagerImpl).waitForInitialized()
            },
            { super.tearDown() },
            { checkTempDirs(oldTempDirRootUrl, newTempDirRootUrl) }
        )
    }

    private fun checkTempDirs(oldTempDirRootUrl: String?, newTempDirRootUrl: String?) {
        // Check that temp root directory was not renamed during the test
        if (oldTempDirRootUrl != null && oldTempDirRootUrl != newTempDirRootUrl) {
            if (newTempDirRootUrl != null) {
                runWriteAction { VirtualFileManager.getInstance().findFileByUrl(newTempDirRootUrl)?.delete(null) }
            }
            error("Root directory has been renamed from `$oldTempDirRootUrl` to `$newTempDirRootUrl`; This should not happens")
        }
    }

    private val rustProjectDescriptor: RustProjectDescriptorBase
        get() {
            val baseDesc = run {
                val annotation = findAnnotationInstance<ProjectDescriptor>() ?: return@run DefaultDescriptor
                return@run (annotation.descriptor.objectInstance
                    ?: error("Only Kotlin objects defined with `object` keyword are allowed"))
            }
            val testWrapping = testWrapping
            if (shouldSkipTestWrapping(testWrapping)) {
                shouldSkipTestWithCurrentWrapping = true
                return baseDesc
            }
            val wrappedDesc = (baseDesc as? RustProjectDescriptorBase)?.let { testWrapping.wrapProjectDescriptor(it) }
            return if (wrappedDesc == null) {
                shouldSkipTestWithCurrentWrapping = true
                baseDesc
            } else {
                wrappedDesc
            }
        }

    private fun setupRustProject() {
        if (projectDescriptor != RustLightProjectDescriptor) {
            RustProjectDescriptorHolder.disposePreviousDescriptor()
            return
        }

        val descriptor = rustProjectDescriptor
        val projectDir = ModuleRootManager.getInstance(myFixture.module).contentEntries.single().file!!

        val key = RustProjectDescriptorKey(project, projectDir, descriptor)

        if (RustProjectDescriptorHolder.previousDescriptorKey != key) {
            RustProjectDescriptorHolder.disposePreviousDescriptor()

            val ws = descriptor.createTestCargoWorkspace(project, projectDir.url)
            if (ws != null) {
                project.testCargoProjects.createTestProject(projectDir, ws, descriptor.rustcInfo)
            } else {
                project.testCargoProjects.removeAllProjects()
            }

            val disposable = module.project.macroExpansionManager.setUnitTestExpansionModeAndDirectory(
                MacroExpansionScope.ALL,
                descriptor.macroExpansionCachingKey.orEmpty()
            )
            @Suppress("IncorrectParentDisposable") // It's fine for a unit test
            Disposer.register(module, disposable)
            RustProjectDescriptorHolder.disposable = disposable

            runWriteAction {
                ProjectRootManagerEx.getInstanceEx(project)
                    .makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.TOTAL_RESCAN)
            }

            RustProjectDescriptorHolder.previousDescriptorKey = key
        }
        descriptor.setUp(myFixture)
    }

    private fun setupMockRustcVersion() {
        val annotation = findAnnotationInstance<MockRustcVersion>() ?: return
        val (semVer, channel) = parse(annotation.rustcVersion)
        project.testCargoProjects.setRustcVersion(RustcVersion(semVer, "", channel), testRootDisposable)
    }

    private fun setupMockEdition() {
        val edition = findAnnotationInstance<MockEdition>()?.edition ?: DEFAULT_EDITION_FOR_TESTS
        project.testCargoProjects.setEdition(edition, testRootDisposable)
    }

    private fun setupMockCfgOptions() {
        val additionalOptions = findAnnotationInstance<MockAdditionalCfgOptions>()?.options
            ?.takeIf { it.isNotEmpty() } ?: return

        val allOptions = CfgOptions(
            CfgOptions.DEFAULT.keyValueOptions,
            CfgOptions.DEFAULT.nameOptions + additionalOptions
        )
        project.testCargoProjects.setCfgOptions(allOptions, testRootDisposable)
    }

    private fun setupMockCargoFeatures() {
        val featuresToParse = findAnnotationInstance<MockCargoFeatures>()?.features
            ?.takeIf { it.isNotEmpty() } ?: return

        val testCargoProjects = project.testCargoProjects

        val packages = testCargoProjects.allProjects.asSequence()
            .flatMap { it.workspace?.packages.orEmpty().asSequence() }
            .associateBy { it.name }

        val packageFeatures = featuresToParse.mapNotNull { featureWithDeps ->
            val i = featureWithDeps.indexOf('=')
            val feature = if (i == -1) featureWithDeps else featureWithDeps.substring(0, i).trim()
            val (pkgName, featureName) = if ("/" in feature) {
                feature.split('/', limit = 2)
            } else {
                listOf("test-package", feature)
            }
            val pkg = packages[pkgName] ?: return@mapNotNull null
            val packageFeature = PackageFeature(pkg, featureName)

            val deps = if (i == -1) {
                emptyList()
            } else {
                featureWithDeps.substring(i + 1, featureWithDeps.length)
                    .trim()
                    .removePrefix("[")
                    .removeSuffix("]")
                    .trim()
                    .split(',')
                    .map { it.trim() }
            }

            packageFeature to deps
        }.toMap()

        testCargoProjects.setCargoFeatures(packageFeatures, testRootDisposable)
    }

    private fun setupExperimentalFeatures() {
        for (feature in findAnnotationInstance<WithExperimentalFeatures>()?.features.orEmpty()) {
            setExperimentalFeatureEnabled(feature, true, testRootDisposable)
        }

        for (feature in findAnnotationInstance<WithoutExperimentalFeatures>()?.features.orEmpty()) {
            setExperimentalFeatureEnabled(feature, false, testRootDisposable)
        }

        if (testWrapping != TestWrapping.NONE) {
            setExperimentalFeatureEnabled(RsExperiments.PROC_MACROS, true, testRootDisposable)
        }
    }

    private fun setupInspections() {
        for (inspection in findAnnotationInstance<WithEnabledInspections>()?.inspections.orEmpty()) {
            enableInspectionTool(project, inspection.createInstance(), testRootDisposable)
        }
    }

    private fun setupExcludedPaths() {
        val annotation = findAnnotationInstance<WithExcludedPath>() ?: return
        val exclusionType = if (annotation.onlyMethods) ExclusionType.Methods else ExclusionType.ItemsAndMethods
        val excludedPath = ExcludedPath(annotation.path, exclusionType)

        val settings = RsCodeInsightSettings.getInstance()
        val initialValue = settings.getExcludedPaths()
        settings.setExcludedPaths(initialValue + arrayOf(excludedPath))
        Disposer.register(testRootDisposable) {
            settings.setExcludedPaths(initialValue)
        }
    }

    private fun parse(version: String): Pair<SemVer, RustChannel> {
        val versionRe = """(\d+\.\d+\.\d+)(.*)""".toRegex()
        val result = versionRe.matchEntire(version) ?: error("$version should match `${versionRe.pattern}` pattern")

        val versionText = result.groups[1]?.value ?: error("")
        val semVer = versionText.parseSemVer()

        val releaseSuffix = result.groups[2]?.value.orEmpty()
        val channel = when {
            releaseSuffix.isEmpty() -> RustChannel.STABLE
            releaseSuffix.startsWith("-beta") -> RustChannel.BETA
            releaseSuffix.startsWith("-nightly") -> RustChannel.NIGHTLY
            else -> RustChannel.DEFAULT
        }
        return semVer to channel
    }

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        val reason = skipTestReason
        if (reason != null) {
            System.err.println("SKIP \"$name\": $reason")
            return
        }

        val ignoreAnnotation = findAnnotationInstance<IgnoreInPlatform>()
        if (ignoreAnnotation != null) {
            val majorPlatformVersion = ApplicationInfo.getInstance().build.baselineVersion
            if (majorPlatformVersion in ignoreAnnotation.majorVersions) {
                System.err.println("SKIP \"$name\": test is ignored for `$majorPlatformVersion` platform")
                return
            }
        }

        val testmark = collectTestmarksFromAnnotations()

        try {
            if (findAnnotationInstance<BothEditions>() != null) {
                if (findAnnotationInstance<MockEdition>() != null) {
                    error("Can't mix `BothEditions` and `MockEdition` annotations")
                }
                // These functions exist to simplify stacktrace analyzing
                testmark.checkHit { runTestEdition2015(testRunnable) }
                testmark.checkHit { runTestEdition2018(testRunnable) }
            } else {
                testmark.checkHit { super.runTestRunnable(testRunnable) }
            }
        } catch (t: Throwable) {
            val testWrapping = testWrapping
            if (testWrapping != TestWrapping.NONE) {
                println("Note: this test was run using `${TestWrapping::class.simpleName}.${testWrapping.name}`.")
                println("      This means that the test content was $testWrapping.")
                println("      If the test is not supposed to work with wrapping, use " +
                    "`@${SkipTestWrapping::class.simpleName}` annotation")
                System.out.flush()
            }
            throw t
        }
    }

    protected open val skipTestReason: String?
        get() {
            val projectDescriptor = rustProjectDescriptor

            fun getMinRustVersionReason(): String? {
                if (shouldSkipTestWithCurrentWrapping) {
                    return "this test is marked to skip the wrapping `${testWrapping.name}`"
                }
                return checkRustcVersionRequirements {
                    val rustcVersion = projectDescriptor?.rustcInfo?.version?.semver
                    if (rustcVersion != null) RsResult.Ok(rustcVersion) else RsResult.Err(null)
                }
            }

            return projectDescriptor.skipTestReason
                ?: getMinRustVersionReason()
        }

    private fun shouldSkipTestWrapping(testWrapping: TestWrapping): Boolean {
        if (testWrapping == TestWrapping.NONE) return false
        val skipWrapping = findAnnotationInstance<SkipTestWrapping>()
        return skipWrapping != null && (skipWrapping.wrapping.isEmpty() || testWrapping in skipWrapping.wrapping)
    }

    private fun runTestEdition2015(testRunnable: ThrowableRunnable<Throwable>) {
        project.testCargoProjects.withEdition(Edition.EDITION_2015) {
            super.runTestRunnable(testRunnable)
        }
    }

    private fun runTestEdition2018(testRunnable: ThrowableRunnable<Throwable>) {
        project.testCargoProjects.withEdition(Edition.EDITION_2018) {
            super.runTestRunnable(testRunnable)
        }
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

        val afterDir = getVirtualFileByName("$testDataPath/$after") ?: error("Failed find `$testDataPath/$after`")
        PlatformTestUtil.assertDirectoriesEqual(afterDir, beforeDir)
    }

    protected fun checkByDirectory(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        expectError: Boolean = false,
        action: (TestProject) -> Unit
    ) {
        val testProject = fileTreeFromText(before).create()
        action(testProject)
        if (expectError) return
        saveAllDocuments()
        fileTreeFromText(after).assertEquals(myFixture.findFileInTempDir("."))
    }

    protected fun checkByText(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        fileName: String = "main.rs",
        action: () -> Unit
    ) {
        InlineFile(before, fileName)
        action()
        PsiTestUtil.checkPsiStructureWithCommit(myFixture.file, PsiTestUtil::checkPsiMatchesTextIgnoringNonCode)
        myFixture.checkResult(replaceCaretMarker(after))
    }

    protected fun checkByTextWithLiveTemplate(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        toType: String,
        fileName: String = "main.rs",
        action: () -> Unit
    ) {
        val actionWithTemplate = {
            action()
            assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.editor))
            myFixture.type(toType)
            assertNull(TemplateManagerImpl.getTemplateState(myFixture.editor))
        }
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        checkByText(before, after, fileName, actionWithTemplate)
    }

    protected fun checkCaretMove(
        @Language("Rust") code: String,
        action: () -> Unit
    ) {
        InlineFile(code.replace("/*caret_before*/", "<caret>").replace("/*caret_after*/", ""))
        action()
        myFixture.checkResult(code.replace("/*caret_after*/", "<caret>").replace("/*caret_before*/", ""))
    }

    protected open fun checkEditorAction(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        actionId: String,
        trimIndent: Boolean = true,
    ) {
        fun String.trimIndentIfNeeded(): String = if (trimIndent) trimIndent() else this

        if ("//-" in before) {
            checkByDirectory(before, after) {
                myFixture.configureFromTempProjectFile(it.fileWithCaret)
                myFixture.performEditorAction(actionId)
            }
        } else {
            checkByText(before.trimIndentIfNeeded(), after.trimIndentIfNeeded()) {
                myFixture.performEditorAction(actionId)
            }
        }
    }

    protected fun openFileInEditor(path: String) {
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir(path))
    }

    private fun getVirtualFileByName(path: String): VirtualFile? =
        LocalFileSystem.getInstance().findFileByPath(path)

    protected inline fun <reified X : Throwable> expect(f: () -> Unit) = org.rust.expect<X>(f)

        @Suppress("TestFunctionName")
    protected fun InlineFile(@Language("Rust") code: String, name: String = "main.rs"): InlineFile {
        val (code2, unwrapper) = testWrapping.wrapCode(project, code)
        val inlineFile = InlineFile(myFixture, code2, name)
        unwrapper?.init(myFixture.file)
        this.testWrappingUnwrapper = unwrapper
        return inlineFile
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

    protected inline fun <reified T : PsiElement> findElementAndOffsetInEditor(marker: String = "^"): Pair<T, Int> {
        val (element, _, offset) = findElementWithDataAndOffsetInEditor<T>(marker)
        return element to offset
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
        return findElementsWithDataAndOffsetInEditor(
            myFixture.file,
            myFixture.file.document!!,
            followMacroExpansions,
            psiClass,
            marker
        )
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

    /**
     * Holds a static instance of [RustProjectDescriptorBase] between tests in order to speed up tests.
     *
     * (This is similar to [com.intellij.testFramework.LightPlatformTestCase.ourProject], see
     * [com.intellij.testFramework.LightPlatformTestCase.doSetup])
     */
    private object RustProjectDescriptorHolder {
        var previousDescriptorKey: RustProjectDescriptorKey? = null
        var disposable: Disposable? = null

        fun disposePreviousDescriptor() {
            previousDescriptorKey = null
            val disposable = disposable
            if (disposable != null) {
                Disposer.dispose(disposable)
                this.disposable = null
                checkMacroExpansionFileSystemAfterTest()
            }
        }
    }

    private data class RustProjectDescriptorKey(
        val project: Project,
        val projectDir: VirtualFile,
        val descriptor: RustProjectDescriptorBase,
    )

    private object RustLightProjectDescriptor : LightProjectDescriptor()

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

            return stream.bufferedReader().use { it.readText() }
        }
    }

    protected fun FileTree.create(): TestProject = create(myFixture)
    protected fun FileTree.createAndOpenFileWithCaretMarker(): TestProject = createAndOpenFileWithCaretMarker(myFixture)

    protected val PsiElement.lineNumber: Int
        get() = myFixture.getDocument(myFixture.file).getLineNumber(textOffset)
}
