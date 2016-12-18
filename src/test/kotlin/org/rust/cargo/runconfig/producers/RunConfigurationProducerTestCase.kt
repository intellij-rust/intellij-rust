package org.rust.cargo.runconfig.producers

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.project.workspace.impl.CargoProjectWorkspaceImpl
import org.rust.cargo.runconfig.CargoCommandConfiguration
import org.rust.cargo.toolchain.impl.CleanCargoMetadata
import org.rust.lang.RustTestCaseBase

class RunConfigurationProducerTestCase : RustTestCaseBase() {
    override val dataPath: String get() = "org/rust/cargo/runconfig/producers/fixtures"
    // We need to override this because we call [CargoProjectWorkspaceImpl.setState].
    override fun getProjectDescriptor(): LightProjectDescriptor = LightProjectDescriptor()

    fun testExecutableProducerWorksForBin() {
        testProject {
            bin("hello", "src/main.rs").open()
        }
        doTestProducedConfigurations()
    }

    fun testExecutableProducerWorksForBinFile() {
        testProject {
            bin("hello", "src/main.rs").open()
        }
        doTestProducedConfigurations(wholeFileIsContext = true)
    }


    fun testExecutableProducerWorksForExample() {
        testProject {
            example("hello", "example/hello.rs").open()
        }
        doTestProducedConfigurations()
    }

    fun testExecutableProducerDisabledForLib() {
        testProject {
            lib("hello", "src/lib.rs").open()
        }
        doTestProducedConfigurations()
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
        doTestProducedConfigurations()
    }

    fun testTestProducerDisableForNonAnnotatedFunctions() {
        testProject {
            lib("foo", "src/lib.rs", "fn test_foo() { <caret>assert!(true); }").open()
        }
        doTestProducedConfigurations()
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
        doTestProducedConfigurations()
    }

    fun testTestProducerWorksForFiles() {
        testProject {
            test("foo", "tests/foo.rs").open()
        }
        doTestProducedConfigurations(wholeFileIsContext = true)
    }

    fun testTestProducerWorksForRootModule() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[test] fn bar() {}

                #[test] fn baz() {}

                fn quux() {<caret>}
            """).open()
        }
        doTestProducedConfigurations()
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
        doTestProducedConfigurations()
    }

    fun testTestProducerAddsBinName() {
        testProject {
            bin("foo", "src/bin/foo.rs", "#[test]\nfn test_foo() { as<caret>sert!(true); }").open()
        }
        doTestProducedConfigurations()
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
        doTestProducedConfigurations()
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
        doTestProducedConfigurations()
    }

    fun testHyphenInNameWorks() {
        testProject {
            example("hello-world", "example/hello.rs").open()
        }
        doTestProducedConfigurations()
    }

    private fun doTestProducedConfigurations(wholeFileIsContext: Boolean = false) {
        val configurationContext = ConfigurationContext(
            if (wholeFileIsContext) myFixture.file else myFixture.file.findElementAt(myFixture.caretOffset)
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
            val kind: CargoProjectDescription.TargetKind
        )

        private var targets = arrayListOf<Target>()
        private var files = arrayListOf<File>()
        private var toOpen: File? = null
        private val helloWorld = """fn main() { println!("Hello, World!") }"""
        private val simpleTest = """#[test] fn test_simple() { assert_eq!(2 + 2, 5) }"""
        private val hello = """pub fn hello() -> String { return "Hello, World!".to_string() }"""

        fun bin(name: String, path: String, code: String = helloWorld): TestProjectBuilder {
            addTarget(name, CargoProjectDescription.TargetKind.BIN, path, code)
            return this
        }

        fun example(name: String, path: String, code: String = helloWorld): TestProjectBuilder {
            addTarget(name, CargoProjectDescription.TargetKind.EXAMPLE, path, code)
            return this
        }

        fun test(name: String, path: String, code: String = simpleTest): TestProjectBuilder {
            addTarget(name, CargoProjectDescription.TargetKind.TEST, path, code)
            return this
        }

        fun lib(name: String, path: String, code: String = hello): TestProjectBuilder {
            addTarget(name, CargoProjectDescription.TargetKind.LIB, path, code)
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

            val metadataService = CargoProjectWorkspace.forModule(myFixture.module) as CargoProjectWorkspaceImpl

            val projectDescription = CargoProjectDescription.deserialize(
                CleanCargoMetadata(
                    packages = listOf(
                        CleanCargoMetadata.Package(
                            myFixture.tempDirFixture.getFile(".")!!.url,
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
                            isWorkspaceMember = true
                        )
                    ),
                    dependencies = emptyList()
                )
            )

            metadataService.setState(projectDescription!!)
        }

        private fun addTarget(name: String, kind: CargoProjectDescription.TargetKind, path: String, code: String) {
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
