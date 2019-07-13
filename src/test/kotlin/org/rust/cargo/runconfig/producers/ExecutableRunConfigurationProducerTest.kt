/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.producers

import com.intellij.execution.configuration.EnvironmentVariablesData
import org.rust.cargo.runconfig.command.CargoExecutableRunConfigurationProducer
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.RustChannel
import org.rust.lang.core.psi.RsFile

class ExecutableRunConfigurationProducerTest : RunConfigurationProducerTestBase() {
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

    fun `test executable configuration uses default environment`() {
        testProject {
            bin("hello", "src/main.rs").open()
        }

        modifyTemplateConfiguration {
            channel = RustChannel.NIGHTLY
            allFeatures = true
            nocapture = true
            emulateTerminal = true
            backtrace = BacktraceMode.FULL
            env = EnvironmentVariablesData.create(mapOf("FOO" to "BAR"), true)
        }

        checkOnTopLevel<RsFile>()
    }
}
