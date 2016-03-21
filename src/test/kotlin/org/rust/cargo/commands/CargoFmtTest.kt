package org.rust.cargo.commands

import org.assertj.core.api.Assertions
import org.rust.cargo.CargoTestCaseBase
import org.rust.cargo.project.pathToCargo

class CargoFmtTest : CargoTestCaseBase() {
    override val testDataPath = "src/test/resources/org/rust/cargo/commands/fixtures/hello"

    fun testCargoFmt() {
        val filePath = "src/main.rs"
        val result = Cargo(testProjectJdk?.pathToCargo!!, project.basePath!!).reformatFile("./$filePath")
        val expect = "[Bin] \"${myProject.basePath}/$filePath\"\nrustfmt --write-mode=overwrite --skip-children ./$filePath ${myProject.basePath}/$filePath\n"
        Assertions.assertThat(result.stdout == expect || result.stdout.isEmpty()).isTrue()
    }
}
