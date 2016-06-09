package org.rust.cargo.runconfig.producers

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.psi.PsiElement
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.project.CargoProjectDescriptionData
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.project.workspace.impl.CargoProjectWorkspaceImpl
import org.rust.cargo.runconfig.CargoCommandConfiguration
import org.rust.cargo.util.getComponentOrThrow
import org.rust.lang.RustTestCaseBase

class RunConfigurationProducerTestCase : RustTestCaseBase() {
    override val dataPath: String get() = "org/rust/cargo/runconfig/producers/fixtures"

    fun testExecutableProducerWorksForBin() {
        testProject {
            bin("hello", "src/main.rs").open()
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

    private fun doTestProducedConfigurations() {
        val configurationContext = ConfigurationContext(myFixture.file.findElementAt(myFixture.caretOffset))

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
        private inner class Target(
            val name: String,
            val path: String,
            val code: String,
            val caretOffset: Int?,
            val kind: CargoProjectDescription.TargetKind
        )

        private var targets = arrayListOf<Target>()
        private var toOpen: Target? = null
        private val hello_world = """fn main() { println!("Hello, World!") }"""
        private val hello = """pub fn hello() -> String { return "Hello, World!".to_string() }"""

        fun bin(name: String, path: String, code: String = hello_world): TestProjectBuilder {
            addTarget(code, name, path, CargoProjectDescription.TargetKind.BIN)
            return this
        }

        fun lib(name: String, path: String, code: String = hello): TestProjectBuilder {
            addTarget(code, name, path, CargoProjectDescription.TargetKind.LIB)
            return this
        }

        fun open(): TestProjectBuilder {
            require(toOpen == null)
            toOpen = targets.last()
            return this
        }

        fun build() {
            targets.forEach { myFixture.addFileToProject(it.path, it.code) }
            toOpen?.let { toOpen ->
                openFileInEditor(toOpen.path)
                if (toOpen.caretOffset != null) {
                    myFixture.editor.caretModel.moveToOffset(toOpen.caretOffset)
                }
            }

            val metadataService = myFixture.module.getComponentOrThrow<CargoProjectWorkspace>() as CargoProjectWorkspaceImpl

            val projectDescription = CargoProjectDescription.deserialize(
                CargoProjectDescriptionData(
                    rootPackageIndex = 0,
                    packages = listOf(
                        CargoProjectDescriptionData.Package(
                            myFixture.tempDirFixture.getFile(".")!!.url,
                            name = "test-package",
                            version = "0.0.1",
                            targets = targets.map {
                                CargoProjectDescriptionData.Target(
                                    myFixture.tempDirFixture.getFile(it.path)!!.url,
                                    it.name,
                                    it.kind
                                )
                            },
                            source = null
                        )
                    ),
                    dependencies = emptyList()
                )
            )

            metadataService.setState(projectDescription!!)
        }

        private fun addTarget(code: String, name: String, path: String, kind: CargoProjectDescription.TargetKind) {
            val caret = code.indexOf("<caret>")
            val offset = if (caret == -1) null else caret
            val cleanedCode = code.replace("<caret>", "")
            targets.add(Target(name, path, cleanedCode, offset, kind))
        }
    }
}
