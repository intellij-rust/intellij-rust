/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.rustPsiManager

class RsProfileResolveTest : RsPerformanceTestBase() {

    fun `test Cargo`() = profileResolve(CARGO, "src/cargo/core/resolver/mod.rs")
    fun `test mysql_async`() = profileResolve(MYSQL_ASYNC, "src/conn/mod.rs")
    fun `test clap`() = profileResolve(CLAP, "clap_derive/src/parse.rs")

    // very big file
    fun `test core_arch`() = profileResolve(STDARCH, "crates/core_arch/src/x86/avx512f.rs")

    private fun profileResolve(info: RealProjectInfo, filePath: String) {
        openProject(info)
        myFixture.configureFromTempProjectFile(filePath)

        val references = myFixture.file.descendantsOfType<RsReferenceElement>()
        profile("Resolved all file references") {
            project.rustPsiManager.incRustStructureModificationCount()
            references.forEach { it.reference?.resolve() }
        }
    }
}
