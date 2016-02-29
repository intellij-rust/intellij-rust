package org.rust.cargo.commands

import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.CargoTestCaseBase
import org.rust.cargo.project.pathToCargo

class CargoTestCase : CargoTestCaseBase() {
    override val testDataPath = "src/test/resources/org/rust/cargo/commands/fixtures/hello"

    fun testCargoMetadata() {
        val description = Cargo(testProjectJdk?.pathToCargo!!, project.basePath!!).fullProjectDescription()

        assertThat(description.packages.size == 3)
        for (module in description.packages) {
            val target = module.targets.single()
            assertThat(target.path).startsWith("src")
        }
    }
}

