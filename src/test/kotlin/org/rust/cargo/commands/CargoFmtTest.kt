package org.rust.cargo.commands

import com.intellij.util.PathUtil
import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.RustWithToolchainTestCaseBase
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.RustToolchain

class CargoFmtTest : RustWithToolchainTestCaseBase() {
    override val dataPath = "src/test/resources/org/rust/cargo/commands/fixtures/fmt"

    fun testCargoFmt() = withProject("hello")  {
        val filePath = "src/main.rs"
        val moduleDirectory = PathUtil.getParentPath(module.moduleFilePath)

        val result = module.project.toolchain!!.cargo(moduleDirectory).reformatFile("./$filePath")
        assertThat(result.exitCode).isEqualTo(0)
    }
}
