/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.producers

import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.ide.DataManager
import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.LangDataKeys.PSI_ELEMENT_ARRAY
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataProvider
import org.intellij.lang.annotations.Language
import org.jdom.Element
import org.rust.RsTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspace.CrateType
import org.rust.cargo.project.workspace.CargoWorkspace.TargetKind
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
import org.rust.cargo.runconfig.command.CargoExecutableRunConfigurationProducer
import org.rust.cargo.runconfig.test.CargoTestRunConfigurationProducer
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.openapiext.toXmlString
import java.nio.file.Paths

class RunConfigurationProducerTest : RsTestBase() {
    override val dataPath: String = "org/rust/cargo/runconfig/producers/fixtures"

    // We need to override this because we call [CargoProjectWorkspaceServiceImpl.setRawWorkspace].
    override fun getProjectDescriptor(): LightProjectDescriptor = LightProjectDescriptor()

    fun `test executable producer works for bin`() {
        testProject {
            bin("hello", "src/main.rs").open()
        }
        checkOnTopLevel<RsFile>()
    }

    fun `test executable producer works for example`() {
        testProject {
            example("hello", "example/hello.rs").open()
        }
        checkOnLeaf()
    }

    fun `test executable producer disabled for lib`() {
        testProject {
            lib("hello", "src/lib.rs").open()
        }
        checkOnLeaf()
    }

    fun `test executable producer remembers context`() {
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

    fun `test test producer works for annotated functions`() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[test]
                fn test_foo() { as/*caret*/sert!(true); }
            """).open()
        }
        checkOnTopLevel<RsFunction>()
    }

    fun `test producer use complete function path`() {
        testProject {
            lib("foo", "src/lib.rs", """
            mod foo_mod {
                #[test]
                fn test_foo() { as/*caret*/sert!(true); }
            }
            """).open()
        }
        checkOnTopLevel<RsFunction>()
    }

    fun `test test producer disable for non annotated functions`() {
        testProject {
            lib("foo", "src/lib.rs", "fn test_foo() { /*caret*/assert!(true); }").open()
        }
        checkOnLeaf()
    }

    fun `test test producer remembers context`() {
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

    fun `test test producer works for modules`() {
        testProject {
            lib("foo", "src/lib.rs", """
                mod foo {
                    #[test] fn bar() {}

                    #[test] fn baz() {}

                    fn quux() {/*caret*/}
                }
            """).open()
        }
        checkOnTopLevel<RsMod>()
    }

    fun `test test producer works for files`() {
        testProject {
            test("foo", "tests/foo.rs").open()
        }
        checkOnElement<RsFile>()
    }

    fun `test test producer works for root module`() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[test] fn bar() {}

                #[test] fn baz() {}

                fn quux() {/*caret*/}
            """).open()
        }
        checkOnLeaf()
    }

    fun `test meaningful configuration name`() {
        testProject {
            lib("foo", "src/lib.rs", "mod bar;")
            file("src/bar/mod.rs", """
                mod tests {
                    fn quux() /*caret*/{}

                    #[test] fn baz() {}
                }
            """).open()
        }
        checkOnLeaf()
    }

    fun `test take into account path attribute`() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[cfg(test)]
                #[path = "foo.rs"]
                mod test;
            """)
            file("src/foo.rs", """
                #[test]
                fn foo() {/*caret*/}
            """).open()
        }
        checkOnTopLevel<RsFunction>()
    }

    fun `test test producer adds bin name`() {
        testProject {
            bin("foo", "src/bin/foo.rs", """
                #[test]
                fn test_foo() { as/*caret*/sert!(true); }
            """).open()
        }
        checkOnLeaf()
    }

    fun `test main fn is more specific than test mod`() {
        testProject {
            bin("foo", "src/main.rs", """
                fn main() { /*caret*/ }
                fn foo() {}
                #[test]
                fn test_foo() {}
            """).open()
        }
        checkOnLeaf()
    }

    fun `test main mod and test mod have same specificity`() {
        testProject {
            bin("foo", "src/main.rs", """
                fn main() {}
                fn foo() { /*caret*/ }
                #[test]
                fn test_foo() {}
            """).open()
        }
        checkOnLeaf()
    }

    fun `test hyphen in name works`() {
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
            env = EnvironmentVariablesData.create(mapOf("FOO" to "BAR"), true)
        }

        checkOnTopLevel<RsFile>()
    }

    fun `test test configuration uses default environment`() {
        testProject {
            lib("foo", "src/lib.rs", """
                #[test]
                fn test_foo() { as/*caret*/sert!(true); }
            """).open()
        }

        modifyTemplateConfiguration {
            env = EnvironmentVariablesData.create(mapOf("FOO" to "BAR"), true)
        }

        checkOnTopLevel<RsFunction>()
    }

    fun `test test producer works for multiple files`() {
        testProject {
            test("foo", "tests/foo.rs", """
                #[test] fn test_foo() {}
            """)

            test("bar", "tests/bar.rs", """
                #[test] fn test_bar() {}
            """)

            test("baz", "tests/baz.rs", """
                #[test] fn test_baz() {}
            """)
        }

        openFileInEditor("tests/foo.rs")
        val file1 = myFixture.file
        openFileInEditor("tests/baz.rs")
        val file2 = myFixture.file
        checkOnFiles(file1, file2)
    }

    fun `test test producer ignores selected files that contain no tests`() {
        testProject {
            test("foo", "tests/foo.rs", """
                #[test] fn test_foo() {}
            """)

            test("bar", "tests/bar.rs", """
                fn test_bar() {}
            """)

            test("baz", "tests/baz.rs", """
                #[test] fn test_baz() {}
            """)
        }

        openFileInEditor("tests/foo.rs")
        val file1 = myFixture.file
        openFileInEditor("tests/bar.rs")
        val file2 = myFixture.file
        checkOnFiles(file1, file2)
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

    private fun checkOnFiles(vararg files: PsiFile) {
        Disposer.register(testRootDisposable, Disposable {
            IdeaTestApplication.getInstance().setDataProvider(null)
        })

        IdeaTestApplication.getInstance().setDataProvider(object : TestDataProvider(project) {
            override fun getData(dataId: String?): Any? =
                if (PSI_ELEMENT_ARRAY.`is`(dataId)) files else super.getData(dataId)
        })

        val dataContext = DataManager.getInstance().getDataContext(myFixture.editor.component)
        val configurationContext = ConfigurationContext.getFromContext(dataContext)
        check(configurationContext)
    }

    private inline fun <reified T : PsiElement> checkOnElement() {
        val configurationContext = ConfigurationContext(
            myFixture.file.findElementAt(myFixture.caretOffset)?.ancestorOrSelf<T>()
        )
        check(configurationContext)
    }

    private fun check(configurationContext: ConfigurationContext) {
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

        assertSameLinesWithFile("$testDataPath/${getTestName(true)}.xml", root.toXmlString())
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
            check(producer.isConfigurationFromContext(configs[i], contexts[i])) {
                "Configuration created from context does not believe it"
            }

            check(!producer.isConfigurationFromContext(configs[i], contexts[1 - i])) {
                "Configuration wrongly believes it is from another context"
            }
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
            val kind: CargoWorkspace.TargetKind,
            val crateTypes: List<CargoWorkspace.CrateType>
        )

        private var targets = arrayListOf<Target>()
        private var files = arrayListOf<File>()
        private var toOpen: File? = null
        private val helloWorld = """fn main() { println!("Hello, World!") }"""
        private val simpleTest = """#[test] fn test_simple() { assert_eq!(2 + 2, 5) }"""
        private val hello = """pub fn hello() -> String { return "Hello, World!".to_string() }"""

        fun bin(name: String, path: String, @Language("Rust") code: String = helloWorld): TestProjectBuilder {
            addTarget(name, TargetKind.BIN, CrateType.BIN, path, code)
            return this
        }

        fun example(name: String, path: String, @Language("Rust") code: String = helloWorld): TestProjectBuilder {
            addTarget(name, TargetKind.EXAMPLE, CrateType.BIN, path, code)
            return this
        }

        fun test(name: String, path: String, @Language("Rust") code: String = simpleTest): TestProjectBuilder {
            addTarget(name, TargetKind.TEST, CrateType.BIN, path, code)
            return this
        }

        fun lib(name: String, path: String, @Language("Rust") code: String = hello): TestProjectBuilder {
            addTarget(name, TargetKind.LIB, CrateType.LIB, path, code)
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
                                    it.crateTypes
                                )
                            },
                            source = null,
                            origin = PackageOrigin.WORKSPACE
                        )
                    ),
                    dependencies = emptyMap()
                )
            )

            project.cargoProjects.createTestProject(myFixture.findFileInTempDir("."), projectDescription)
        }

        private fun addTarget(name: String, kind: CargoWorkspace.TargetKind, crateType: CargoWorkspace.CrateType, path: String, code: String) {
            val file = addFile(path, code)
            targets.add(Target(name, file, kind, listOf(crateType)))
        }

        private fun addFile(path: String, code: String): File {
            val caret = code.indexOf("/*caret*/")
            val offset = if (caret == -1) null else caret
            val cleanedCode = code.replace("/*caret*/", "")
            return File(path, cleanedCode, offset).also { files.add(it) }
        }
    }
}
