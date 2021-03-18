/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import org.rust.toml.crates.local.CargoRegistryCrate

class LocalCargoTomlDependencyVersionCompletionTest : LocalCargoTomlCompletionTestBase() {
    fun `test complete version`() = doFirstCompletion("""
        [dependencies]
        foo = "<caret>"
    """, """
        [dependencies]
        foo = "0.1.0<caret>"
    """, "foo" to CargoRegistryCrate.of("0.1.0")
    )

    fun `test complete highest by semver version`() = doFirstCompletion("""
        [dependencies]
        foo = "<caret>"
    """, """
        [dependencies]
        foo = "1.0.0<caret>"
    """, "foo" to CargoRegistryCrate.of("0.0.1", "1.0.0", "0.1.0")
    )

    fun `test complete sorted by semver versions`() = completeBasic(
        """
        [dependencies]
        foo = "<caret>"
        """,
        listOf("0.4.7", "0.4.6", "0.4.5", "0.4.4", "0.4.3", "0.4.2", "0.4.1", "0.4.0", "0.4.0-rc.2", "0.4.0-rc.1", "0.4.0-rc", "0.3.17", "0.3.16", "0.3.15", "0.3.14", "0.3.13", "0.3.12", "0.3.11", "0.3.10", "0.3.9", "0.3.8", "0.3.7", "0.3.6", "0.3.5", "0.3.4", "0.3.3", "0.3.2", "0.3.1", "0.3.0", "0.2.11", "0.2.10", "0.2.9", "0.2.8", "0.2.7", "0.2.6", "0.2.5", "0.2.4", "0.2.3", "0.2.2", "0.2.1", "0.2.0", "0.1.6", "0.1.5", "0.1.4", "0.1.3", "0.1.2", "0.1.1", "0.1.0"),
        "foo" to CargoRegistryCrate.of("0.1.4", "0.4.3", "0.3.0", "0.1.1", "0.4.0", "0.3.5", "0.4.0-rc.1", "0.1.0", "0.4.2", "0.3.3", "0.3.4", "0.2.0", "0.1.3", "0.2.5", "0.3.8", "0.2.6", "0.4.6", "0.1.5", "0.2.11", "0.2.2", "0.4.5", "0.2.1", "0.3.1", "0.3.16", "0.3.6", "0.2.10", "0.4.4", "0.3.12", "0.2.9", "0.3.10", "0.1.6", "0.3.14", "0.2.4", "0.3.2", "0.1.2", "0.3.17", "0.3.9", "0.4.0-rc.2", "0.4.1", "0.3.15", "0.3.7", "0.4.0-rc", "0.2.7", "0.2.3", "0.4.7", "0.3.11", "0.2.8", "0.3.13"),
    )
}
