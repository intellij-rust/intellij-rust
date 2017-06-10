package org.rust.cargo

import com.intellij.util.PathUtil
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.project.settings.toolchain

class RsCargoCheckAnnotatorTest : RustWithToolchainTestBase() {
    override val dataPath = "src/test/resources/org/rust/cargo/check/fixtures"

    fun testZeroErrorCodeIfProjectHasNoErrors() = withProject("hello") {
        val dir = cargoProjectDirectory.path
        val cmd = myModule.project.toolchain!!.cargo(dir).checkCommandline()
        val result = myModule.project.toolchain!!.cargo(dir).checkProject(testRootDisposable)

        if (result.exitCode != 0) {
            TestCase.fail("Expected zero error code, but got ${result.exitCode}. " +
                "cmd = ${cmd.commandLineString}, stdout = ${result.stdout}, stderr = ${result.stderr}")
        }
    }

    fun testNonZeroErrorCodeIfProjectHasErrors() = withProject("errors") {
        val moduleDirectory = PathUtil.getParentPath(myModule.moduleFilePath)
        val result = myModule.project.toolchain!!.cargo(moduleDirectory).checkProject(testRootDisposable)
        assertThat(result.exitCode).isNotEqualTo(0)
    }
}
