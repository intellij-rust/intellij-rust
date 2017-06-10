package org.rust.cargo.commands

import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.cargo.project.settings.toolchain

class CargoFmtTest : RustWithToolchainTestBase() {
    override val dataPath = "src/test/resources/org/rust/cargo/commands/fixtures/fmt"

    fun testCargoFmt() = withProject("hello") {
        val filePath = "src/main.rs"
        val cargo = myModule.project.toolchain!!.cargo(cargoProjectDirectory.path)
        val result = cargo.reformatFile(testRootDisposable, "./$filePath")
        assertThat(result.exitCode).isEqualTo(0)
    }
}
