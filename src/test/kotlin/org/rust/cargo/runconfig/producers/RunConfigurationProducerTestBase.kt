/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.producers

import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.LangDataKeys.PSI_ELEMENT_ARRAY
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataProvider
import org.intellij.lang.annotations.Language
import org.jdom.Element
import org.rust.RsTestBase
import org.rust.cargo.CargoConstants
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.impl.testCargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspace.*
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.openapiext.toXmlString
import java.nio.file.Paths

abstract class RunConfigurationProducerTestBase : RsTestBase() {
    override val dataPath: String = "org/rust/cargo/runconfig/producers/fixtures"

    override fun getProjectDescriptor(): LightProjectDescriptor = ProjectDescriptor()

    protected fun modifyTemplateConfiguration(f: CargoCommandConfiguration.() -> Unit) {
        val configurationType = CargoCommandConfigurationType.getInstance()
        val factory = configurationType.factory
        val template = RunManager.getInstance(project).getConfigurationTemplate(factory).configuration as CargoCommandConfiguration
        template.f()
    }

    protected fun checkOnLeaf() = checkOnElement<PsiElement>()

    protected inline fun <reified T : PsiElement> checkOnTopLevel() {
        checkOnElement<T>()
        checkOnElement<PsiElement>()
    }

    protected fun checkOnFiles(vararg files: PsiElement) {
        TestApplicationManager.getInstance().setDataProvider(object : TestDataProvider(project) {
            override fun getData(dataId: String): Any? =
                if (PSI_ELEMENT_ARRAY.`is`(dataId)) files else super.getData(dataId)
        }, testRootDisposable)

        val dataContext = DataManager.getInstance().getDataContext(myFixture.editor.component)
        val configurationContext = ConfigurationContext.getFromContext(dataContext)
        check(configurationContext)
    }

    protected inline fun <reified T : PsiElement> checkOnElement() {
        val configurationContext = ConfigurationContext(
            myFixture.file.findElementAt(myFixture.caretOffset)?.ancestorOrSelf<T>()
        )
        check(configurationContext)
    }

    protected fun check(configurationContext: ConfigurationContext) {
        val configurations = configurationContext.configurationsFromContext.orEmpty().map { it.configurationSettings }

        val root = Element("configurations")
        configurations.forEach {
            val content = (it as RunnerAndConfigurationSettingsImpl).writeScheme()
            root.addContent(content)
        }

        assertSameLinesWithFile("$testDataPath/${getTestName(true)}.xml", root.toXmlString())
    }

    protected fun doTestRemembersContext(
        producer: RunConfigurationProducer<CargoCommandConfiguration>,
        ctx1: PsiElement,
        ctx2: PsiElement
    ) {
        val contexts = listOf(ConfigurationContext(ctx1), ConfigurationContext(ctx2))
        val configsFromContext = contexts.map { it.configurationsFromContext!!.single() }
        configsFromContext.forEach { check(it.isProducedBy(producer.javaClass)) }
        val configs = configsFromContext.map { it.configuration as CargoCommandConfiguration }
        for (i in 0..1) {
            check(producer.isConfigurationFromContext(configs[i], contexts[i])) {
                "Configuration created from context does not believe it"
            }

            check(!producer.isConfigurationFromContext(configs[i], contexts[1 - i])) {
                "Configuration wrongly believes it is from another context"
            }
        }
    }

    protected fun testProject(description: TestProjectBuilder.() -> Unit) {
        val testProject = TestProjectBuilder()
        testProject.description()
        testProject.build()
    }

    protected inner class TestProjectBuilder {
        private inner class File(
            val path: String,
            val code: String,
            val caretOffset: Int?
        )

        private inner class Target(
            val name: String,
            val file: File,
            val kind: TargetKind,
            val edition: Edition
        )

        private var targets = arrayListOf<Target>()
        private var files = arrayListOf<File>()
        private var toOpen: File? = null
        private val helloWorld = """fn main() { println!("Hello, World!") }"""
        private val simpleTest = """#[test] fn test_simple() { assert_eq!(2 + 2, 5) }"""
        private val simpleBench = """#[bench] fn bench_simple() {}"""
        private val hello = """pub fn hello() -> String { return "Hello, World!".to_string(); }"""

        fun bin(name: String, path: String, @Language("Rust") code: String = helloWorld): TestProjectBuilder {
            addTarget(name, TargetKind.Bin, Edition.EDITION_2015, path, code)
            return this
        }

        fun example(name: String, path: String, @Language("Rust") code: String = helloWorld): TestProjectBuilder {
            addTarget(name, TargetKind.ExampleBin, Edition.EDITION_2015, path, code)
            return this
        }

        fun test(name: String, path: String, @Language("Rust") code: String = simpleTest): TestProjectBuilder {
            addTarget(name, TargetKind.Test, Edition.EDITION_2015, path, code)
            return this
        }

        fun bench(name: String, path: String, @Language("Rust") code: String = simpleBench): TestProjectBuilder {
            addTarget(name, TargetKind.Bench, Edition.EDITION_2015, path, code)
            return this
        }

        fun lib(name: String, path: String, @Language("Rust") code: String = hello): TestProjectBuilder {
            addTarget(name, TargetKind.Lib(LibKind.LIB), Edition.EDITION_2015, path, code)
            return this
        }

        fun file(path: String, @Language("Rust") code: String): TestProjectBuilder {
            addFile(path, code)
            return this
        }

        fun open(): TestProjectBuilder {
            require(toOpen == null)
            toOpen = files.last()
            return this
        }

        fun build() {
            myFixture.addFileToProject("Cargo.toml", """
                [project]
                name = "test"
                version = 0.0.1
            """)
            files.forEach { myFixture.addFileToProject(it.path, it.code) }
            toOpen?.let { toOpen ->
                openFileInEditor(toOpen.path)
                if (toOpen.caretOffset != null) {
                    myFixture.editor.caretModel.moveToOffset(toOpen.caretOffset)
                }
            }

            val projectDescription = CargoWorkspace.deserialize(
                Paths.get("/my-crate/Cargo.toml"),
                CargoWorkspaceData(
                    packages = listOf(
                        CargoWorkspaceData.Package(
                            id = "test-package 0.0.1",
                            contentRootUrl = myFixture.tempDirFixture.getFile(".")!!.url,
                            name = "test-package",
                            version = "0.0.1",
                            targets = targets.map {
                                CargoWorkspaceData.Target(
                                    myFixture.tempDirFixture.getFile(it.file.path)!!.url,
                                    it.name,
                                    it.kind,
                                    it.edition,
                                    doctest = true
                                )
                            },
                            source = null,
                            origin = PackageOrigin.WORKSPACE,
                            edition = Edition.EDITION_2015,
                            features = emptyMap(),
                            defaultFeatures = emptySet(),
                            cfgOptions = CfgOptions.EMPTY,
                            env = emptyMap(),
                            outDirUrl = null
                        )
                    ),
                    dependencies = emptyMap()
                ),
                CfgOptions.DEFAULT
            )

            project.testCargoProjects.createTestProject(myFixture.findFileInTempDir("."), projectDescription)
        }

        private fun addTarget(
            name: String,
            kind: TargetKind,
            edition: Edition,
            path: String,
            code: String
        ) {
            val file = addFile(path, code)
            targets.add(Target(name, file, kind, edition))
        }

        private fun addFile(path: String, code: String): File {
            val caret = code.indexOf("/*caret*/")
            val offset = if (caret == -1) null else caret
            val cleanedCode = code.replace("/*caret*/", "")
            return File(path, cleanedCode, offset).also { files.add(it) }
        }
    }

    private class ProjectDescriptor : LightProjectDescriptor() {
        @Throws(Exception::class)
        override fun setUpProject(project: Project, handler: SetupHandler) {
            val setupHandler = object : SetupHandler  {

                private lateinit var module: Module

                override fun moduleCreated(module: Module) {
                    handler.moduleCreated(module)
                    this.module = module
                }

                override fun sourceRootCreated(sourceRoot: VirtualFile) {
                    handler.sourceRootCreated(sourceRoot)
                    val sourceRoots = CargoConstants.ProjectLayout.sources + CargoConstants.ProjectLayout.tests
                    sourceRoots
                        .map { sourceRoot.createChildDirectory(this, it) }
                        .forEach { createContentEntry(module, it) }
                }
            }

            super.setUpProject(project, setupHandler)
        }
    }
}
