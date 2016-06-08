package org.rust.cargo.runconfig.producers

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.util.JDOMUtil
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
        val firstContext = ConfigurationContext(myFixture.file)
        val configs = firstContext.configurationsFromContext!!.map { it.configuration }
        check(configs.size > 0)

        openFileInEditor("bin/bar.rs")
        val secondContext = ConfigurationContext(myFixture.file)

        val producer = CargoExecutableRunConfigurationProducer()
        for (config in configs) {
            val cargoConfig = config as CargoCommandConfiguration
            assertThat(producer.isConfigurationFromContext(cargoConfig, firstContext)).isTrue()
            assertThat(producer.isConfigurationFromContext(cargoConfig, secondContext)).isFalse()
        }
    }

    private fun doTestProducedConfigurations() {
        val configurationContext = ConfigurationContext(myFixture.file)

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
            val kind: CargoProjectDescription.TargetKind
        )

        private var targets = arrayListOf<Target>()
        private var toOpen: String? = null
        private val hello_world = """fn main() { println!("Hello, World!") }"""
        private val hello = """pub fn hello() -> String { return "Hello, World!".to_string() }"""

        fun bin(name: String, path: String, code: String = hello_world): TestProjectBuilder {
            targets.add(Target(name, path, code, CargoProjectDescription.TargetKind.BIN))
            return this
        }

        fun lib(name: String, path: String, code: String = hello): TestProjectBuilder {
            targets.add(Target(name, path, code, CargoProjectDescription.TargetKind.LIB))
            return this
        }

        fun open(): TestProjectBuilder {
            require(toOpen == null)
            toOpen = targets.last().path
            return this
        }

        fun build() {
            for (target in targets) {
                myFixture.addFileToProject(target.path, target.code)
            }
            toOpen?.let { openFileInEditor(it) }

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
    }
}
