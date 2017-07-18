/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.producers

import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.rust.cargo.project.workspace.CargoProjectWorkspaceService
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.impl.CargoProjectWorkspaceServiceImpl
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
import org.rust.cargo.runconfig.command.CargoExecutableRunConfigurationProducer
import org.rust.cargo.runconfig.test.CargoTestRunConfigurationProducer
import org.rust.cargo.toolchain.impl.CleanCargoMetadata
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.parentOfType

class RunConfigurationProducerTest : RsTestBase() {
    override val dataPath: String get() {
        val info = ApplicationInfo.getInstance()
        // BACKCOMPAT: 2017.1
        // HACK: IDEA 2017.2 produces a little bit different configuration xml
        // so fixtures for 2017.1 were moved into separate folder
        val compatibilityFolder = if (info.majorVersion == "2017" && info.minorVersion == "1") "/2017.1" else ""
        return "org/rust/cargo/runconfig/producers/fixtures$compatibilityFolder"
    }
    // We need to override this because we call [CargoProjectWorkspaceServiceImpl.setRawWorkspace].
    override fun getProjectDescriptor(): LightProjectDescriptor = LightProjectDescriptor()

    fun testExecutableProducerWorksForBin() {
        testProject {
            bin("hello", "src/main.rs").open()
        }
        checkOnTopLevel<RsFile>()
    }

    fun testExecutableProducerWorksForExample() {
        testProject {
            example("hello", "example/hello.rs").open()
        }
        checkOnLeaf()
    }

    fun testExecutableProducerDisabledForLib() {
        testProject {
            lib("hello", "src/lib.rs").open()
        }
        checkOnLeaf()
    }

    fun testExecutableProducerRemembersContext() {
        testProject {
            bin("foo", "bin/foo.rs")
            bin("bar", "bin/bar.rs")
        }

        openFileInEditor("bin/foo.rs")
        val ctx1 = myFixture.file
        openFileInEditor("bin/bar.rs")
        val ctx2 = myFixture.file
        doTestRemembersContext(CargoExecutableRunConfigurationProducer(), ctx1, ctx2)
    }

    fun testTestProducerWorksForAnnotatedFunctions() {
        testProject {
            lib("foo", "src/lib.rs", "#[test]\nfn test_foo() { as<caret>sert!(true); }").open()
        }
        checkOnTopLevel<RsFunction>()
    }

    fun testTestProducerDisableForNonAnnotatedFunctions() {
        testProject {
            lib("foo", "src/lib.rs", "fn test_foo() { <caret>assert!(true); }").open()
        }
        checkOnLeaf()
    }

    fun testTestProducerRemembersContext() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[test]
                fn test_foo() {
                    assert_eq!(2 + 2, 4);
                }

                #[test]
                fn test_bar() {
                    assert_eq!(2 * 2, 4);
                }
            """).open()
        }

        val ctx1 = myFixture.findElementByText("+", PsiElement::class.java)
        val ctx2 = myFixture.findElementByText("*", PsiElement::class.java)
        doTestRemembersContext(CargoTestRunConfigurationProducer(), ctx1, ctx2)
    }

    fun testTestProducerWorksForModules() {
        testProject {
            lib("foo", "src/lib.rs", """
                mod foo {
                    #[test] fn bar() {}

                    #[test] fn baz() {}

                    fn quux() {<caret>}
                }
            """).open()
        }
        checkOnTopLevel<RsMod>()
    }

    fun testTestProducerWorksForFiles() {
        testProject {
            test("foo", "tests/foo.rs").open()
        }
        checkOnElement<RsFile>()
    }

    fun testTestProducerWorksForRootModule() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[test] fn bar() {}

                #[test] fn baz() {}

                fn quux() {<caret>}
            """).open()
        }
        checkOnLeaf()
    }

    fun testMeaningfulConfigurationName() {
        testProject {
            lib("foo", "src/lib.rs", "mod bar;")
            file("src/bar/mod.rs", """
                mod tests {
                    fn quux() <caret>{}

                    #[test] fn baz() {}
                }
            """).open()
        }
        checkOnLeaf()
    }

    fun testTestProducerAddsBinName() {
        testProject {
            bin("foo", "src/bin/foo.rs", "#[test]\nfn test_foo() { as<caret>sert!(true); }").open()
        }
        checkOnLeaf()
    }

    fun testMainFnIsMoreSpecificThanTestMod() {
        testProject {
            bin("foo", "src/main.rs", """
                fn main() { <caret> }
                fn foo() {}
                #[test]
                fn test_foo() {}
            """).open()
        }
        checkOnLeaf()
    }

    fun testMainModAndTestModHaveSameSpecificity() {
        testProject {
            bin("foo", "src/main.rs", """
                fn main() {}
                fn foo() { <caret> }
                #[test]
                fn test_foo() {}
            """).open()
        }
        checkOnLeaf()
    }

    fun testHyphenInNameWorks() {
        testProject {
            example("hello-world", "example/hello.rs").open()
        }
        checkOnLeaf()
    }

    fun `test executable configuration uses default environment`() {
        testProject {
            bin("hello", "src/main.rs").open()
        }

        modifyTemplateConfiguration {
            cargoCommandLine = cargoCommandLine.copy(environmentVariables = mapOf("FOO" to "BAR"))
        }

        checkOnTopLevel<RsFile>()
    }

    fun `test test configuration uses default environment`() {
        testProject {
            lib("foo", "src/lib.rs", "#[test]\nfn test_foo() { as<caret>sert!(true); }").open()
        }

        modifyTemplateConfiguration {
            cargoCommandLine = cargoCommandLine.copy(environmentVariables = mapOf("FOO" to "BAR"))
        }

        checkOnTopLevel<RsFunction>()
    }

    private fun modifyTemplateConfiguration(f: CargoCommandConfiguration.() -> Unit) {
        val configurationType = ConfigurationTypeUtil.findConfigurationType(CargoCommandConfigurationType::class.java)
        val factory = configurationType.factory
        val template = RunManager.getInstance(project).getConfigurationTemplate(factory).configuration as CargoCommandConfiguration
        template.f()
    }

    private fun checkOnLeaf() = checkOnElement<PsiElement>()

    inline private fun <reified T : PsiElement> checkOnTopLevel() {
        checkOnElement<T>()
        checkOnElement<PsiElement>()
    }

    private inline fun <reified T : PsiElement> checkOnElement() {
        val configurationContext = ConfigurationContext(
            myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<T>(strict = false)
        )

        val configurations = configurationContext.configurationsFromContext.orEmpty().map { it.configuration }

        val serialized = configurations.map { config ->
            Element("configuration").apply {
                setAttribute("name", config.name)
                setAttribute("class", config.javaClass.simpleName)
                config.writeExternal(this)
            }
        }

        val root = Element("configurations")
        serialized.forEach { root.addContent(it) }

        //BACKCOMPAT: in 2017.2, a module is saved to XML as well
        if (ApplicationManager.getApplication().isEAP) return
        assertSameLinesWithFile("$testDataPath/${getTestName(true)}.xml", JDOMUtil.writeElement(root))
    }

    private fun doTestRemembersContext(
        producer: RunConfigurationProducer<CargoCommandConfiguration>,
        ctx1: PsiElement,
        ctx2: PsiElement
    ) {
        val contexts = listOf(ConfigurationContext(ctx1), ConfigurationContext(ctx2))
        val configsFromContext = contexts.map { it.configurationsFromContext!!.single() }
        configsFromContext.forEach { check(it.isProducedBy(producer.javaClass)) }
        val configs = configsFromContext.map { it.configuration as CargoCommandConfiguration }
        for (i in 0..1) {
            assertThat(producer.isConfigurationFromContext(configs[i], contexts[i])).isTrue()
            assertThat(producer.isConfigurationFromContext(configs[i], contexts[1 - i])).isFalse()
        }
    }

    private fun testProject(description: TestProjectBuilder.() -> Unit) {
        val testProject = TestProjectBuilder()
        testProject.description()
        testProject.build()
    }

    private inner class TestProjectBuilder {
        private inner class File(
            val path: String,
            val code: String,
            val caretOffset: Int?
        )

        private inner class Target(
            val name: String,
            val file: File,
            val kind: CargoWorkspace.TargetKind
        )

        private var targets = arrayListOf<Target>()
        private var files = arrayListOf<File>()
        private var toOpen: File? = null
        private val helloWorld = """fn main() { println!("Hello, World!") }"""
        private val simpleTest = """#[test] fn test_simple() { assert_eq!(2 + 2, 5) }"""
        private val hello = """pub fn hello() -> String { return "Hello, World!".to_string() }"""

        fun bin(name: String, path: String, code: String = helloWorld): TestProjectBuilder {
            addTarget(name, CargoWorkspace.TargetKind.BIN, path, code)
            return this
        }

        fun example(name: String, path: String, code: String = helloWorld): TestProjectBuilder {
            addTarget(name, CargoWorkspace.TargetKind.EXAMPLE, path, code)
            return this
        }

        fun test(name: String, path: String, code: String = simpleTest): TestProjectBuilder {
            addTarget(name, CargoWorkspace.TargetKind.TEST, path, code)
            return this
        }

        fun lib(name: String, path: String, code: String = hello): TestProjectBuilder {
            addTarget(name, CargoWorkspace.TargetKind.LIB, path, code)
            return this
        }

        fun file(path: String, code: String): TestProjectBuilder {
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

            val metadataService = CargoProjectWorkspaceService.getInstance(myFixture.module) as CargoProjectWorkspaceServiceImpl

            val projectDescription = CargoWorkspace.deserialize(
                CleanCargoMetadata(
                    packages = listOf(
                        CleanCargoMetadata.Package(
                            id = "test-package 0.0.1",
                            url = myFixture.tempDirFixture.getFile(".")!!.url,
                            name = "test-package",
                            version = "0.0.1",
                            targets = targets.map {
                                CleanCargoMetadata.Target(
                                    myFixture.tempDirFixture.getFile(it.file.path)!!.url,
                                    it.name,
                                    it.kind
                                )
                            },
                            source = null,
                            manifestPath = "/somewhere/test-package/Cargo.toml",
                            isWorkspaceMember = true
                        )
                    ),
                    dependencies = emptyList()
                )
            )

            metadataService.setRawWorkspace(projectDescription)
        }

        private fun addTarget(name: String, kind: CargoWorkspace.TargetKind, path: String, code: String) {
            val file = addFile(path, code)
            targets.add(Target(name, file, kind))
        }

        private fun addFile(path: String, code: String): File {
            val caret = code.indexOf("<caret>")
            val offset = if (caret == -1) null else caret
            val cleanedCode = code.replace("<caret>", "")
            val file = File(path, cleanedCode, offset)
            files.add(file)
            return file
        }
    }
}
