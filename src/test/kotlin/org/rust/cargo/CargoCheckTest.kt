package org.rust.cargo

import com.intellij.util.PathUtil
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.project.settings.toolchain

class CargoCheckTest : RustWithToolchainTestBase() {
    override val dataPath = "src/test/resources/org/rust/cargo/check/fixtures"

    fun testZeroErrorCodeIfProjectHasNoErrors() = withProject("hello") {
        val moduleDirectory = PathUtil.getParentPath(module.moduleFilePath)
        val cmd = module.project.toolchain!!.cargo(moduleDirectory).checkCommandline()
        val result = module.project.toolchain!!.cargo(moduleDirectory).checkProject(testRootDisposable)

        if (result.exitCode != 0) {
            TestCase.fail("Expected zero error code, but got ${result.exitCode}. " +
                "cmd = ${cmd.commandLineString}, stdout = ${result.stdout}, stderr = ${result.stderr}")
        }
    }

    fun testNonZeroErrorCodeIfProjectHasErrors() = withProject("errors") {
        val moduleDirectory = PathUtil.getParentPath(module.moduleFilePath)
        val result = module.project.toolchain!!.cargo(moduleDirectory).checkProject(testRootDisposable)
        assertThat(result.exitCode).isNotEqualTo(0)
    }
}
