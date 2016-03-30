package org.rust.cargo.project

import com.intellij.openapi.application.Result
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.test.ExternalSystemImportingTestCase
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.module.util.findExternCrateByName
import org.rust.cargo.project.module.util.targets
import org.rust.cargo.project.settings.CargoProjectSettings

abstract class CargoImportTestCaseBase : ExternalSystemImportingTestCase() {
    var sdk: Sdk? = null

    override fun setUpInWriteAction() {
        super.setUpInWriteAction()
        createTestSdk()
        if (sdk != null) {
            ProjectJdkTable.getInstance().addJdk(sdk)
        }
    }

    override fun tearDown() {
        super.tearDown()
        object : WriteAction<Nothing>() {
            override fun run(result: Result<Nothing>) {
                if (sdk != null) {
                    val table = ProjectJdkTable.getInstance()
                    table.removeJdk(sdk)
                }
            }
        }.execute()
    }

    override fun getTestsTempDir(): String = "cargoImportTests"

    override fun getExternalSystemConfigFileName(): String = "Cargo.toml"

    override fun getCurrentExternalProjectSettings(): CargoProjectSettings? =
        sdk?.let { CargoProjectSettings(it.name) }

    override fun getExternalSystemId(): ProjectSystemId = CargoConstants.PROJECT_SYSTEM_ID

    override fun runTest() {
        if (currentExternalProjectSettings == null) {
            System.err?.println("SKIP $name: no Rust sdk found")
            return
        }
        super.runTest()
    }

    protected fun assertTargets(vararg paths: String) {
        assertThat(module.targets.map { it.path })
            .containsOnly(*paths)
    }

    protected fun assertExternCrates(vararg crateNames: String) {
        for (name in crateNames) {
            assertThat(module.findExternCrateByName(name))
                .isNotNull()
        }
    }

    private val module: Module get() =
        ModuleManager.getInstance(myProject).modules.single()

    private fun createTestSdk() {
        RustSdkType.INSTANCE.suggestHomePath()?.let {
            sdk = ProjectJdkImpl("RustTestSdk", RustSdkType.INSTANCE, it, "1.8.0")
        }
    }
}

