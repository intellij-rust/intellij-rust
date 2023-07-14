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

    fun `test complete sorted by semver versions`() = completeBasic("""
        [dependencies]
        foo = "<caret>"
    """,
        listOf("0.4.7", "0.4.6", "0.4.5", "0.4.4", "0.4.3", "0.4.2", "0.4.1", "0.4.0", "0.4.0-rc.2", "0.4.0-rc.1", "0.4.0-rc", "0.3.17", "0.3.16", "0.3.15", "0.3.14", "0.3.13", "0.3.12", "0.3.11", "0.3.10", "0.3.9", "0.3.8", "0.3.7", "0.3.6", "0.3.5", "0.3.4", "0.3.3", "0.3.2", "0.3.1", "0.3.0", "0.2.11", "0.2.10", "0.2.9", "0.2.8", "0.2.7", "0.2.6", "0.2.5", "0.2.4", "0.2.3", "0.2.2", "0.2.1", "0.2.0", "0.1.6", "0.1.5", "0.1.4", "0.1.3", "0.1.2", "0.1.1", "0.1.0"),
        "foo" to CargoRegistryCrate.of("0.1.4", "0.4.3", "0.3.0", "0.1.1", "0.4.0", "0.3.5", "0.4.0-rc.1", "0.1.0", "0.4.2", "0.3.3", "0.3.4", "0.2.0", "0.1.3", "0.2.5", "0.3.8", "0.2.6", "0.4.6", "0.1.5", "0.2.11", "0.2.2", "0.4.5", "0.2.1", "0.3.1", "0.3.16", "0.3.6", "0.2.10", "0.4.4", "0.3.12", "0.2.9", "0.3.10", "0.1.6", "0.3.14", "0.2.4", "0.3.2", "0.1.2", "0.3.17", "0.3.9", "0.4.0-rc.2", "0.4.1", "0.3.15", "0.3.7", "0.4.0-rc", "0.2.7", "0.2.3", "0.4.7", "0.3.11", "0.2.8", "0.3.13"),
    )

    fun `test complete sorted order minor, major and patch sections`() = completeBasic("""
        [dependencies]
        foo = "<caret>"
    """,
        // source: semver spec (https://semver.org/)
        listOf("2.1.1", "2.1.0", "2.0.0", "1.0.0"),
        "foo" to CargoRegistryCrate.of("1.0.0", "2.1.0", "2.1.1", "2.0.0")
    )

    fun `test complete sorted by semver with pre-release sections`() = completeBasic("""
        [dependencies]
        foo = "<caret>"
    """,
        // source: semver spec (https://semver.org/)
        listOf("1.0.0" , "1.0.0-rc.1" , "1.0.0-beta.11" , "1.0.0-beta.2" , "1.0.0-beta" , "1.0.0-alpha.beta" , "1.0.0-alpha.1" , "1.0.0-alpha"),
        "foo" to CargoRegistryCrate.of("1.0.0-alpha.1" , "1.0.0-beta" , "1.0.0-beta.2" , "1.0.0-rc.1" , "1.0.0-beta.11" , "1.0.0-alpha" , "1.0.0" , "1.0.0-alpha.beta")
    )

    fun `test empty value complete without '=' and quotes 1_0`() = doSingleCompletion("""
        [dependencies]
        foo <caret>
    """, """
        [dependencies]
        foo = "1.0<caret>"
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test empty value complete without quotes 1_0`() = doSingleCompletion("""
        [dependencies]
        foo = <caret>
    """, """
        [dependencies]
        foo = "1.0<caret>"
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test empty value complete without quotes 1_0_0`() = doSingleCompletion("""
        [dependencies]
        foo = <caret>
    """, """
        [dependencies]
        foo = "1.0.0<caret>"
    """, "foo" to CargoRegistryCrate.of("1.0.0"))

    fun `test empty value complete inside quotes`() = doSingleCompletion("""
        [dependencies]
        foo = "<caret>"
    """, """
        [dependencies]
        foo = "1.0<caret>"
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test partial value complete inside quotes`() = doSingleCompletion("""
        [dependencies]
        foo = "1.<caret>"
    """, """
        [dependencies]
        foo = "1.0<caret>"
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test empty value complete inside quotes without '='`() = doSingleCompletion("""
        [dependencies]
        foo "<caret>"
    """, """
        [dependencies]
        foo = "1.0<caret>"
    """, "foo" to CargoRegistryCrate.of("1.0"))


    fun `test key completion inside inline table`() = doSingleCompletion("""
        [dependencies]
        foo = { vers<caret> }
    """, """
        [dependencies]
        foo = { version = "<caret>" }
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test key completion with existing value inside inline table`() = doSingleCompletion("""
        [dependencies]
        foo = { vers<caret> = "1.0" }
    """, """
        [dependencies]
        foo = { version = "<caret>1.0" }
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test empty value complete after unclosed quote`() = doSingleCompletion("""
        [dependencies]
        foo = "<caret>
    """, """
        [dependencies]
        foo = "1.0<caret>
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test no completion when caret after string literal`() = checkNoCompletion("""
        [dependencies]
        foo = "" <caret>
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test no completion when caret after inline table`() = checkNoCompletion("""
        [dependencies]
        foo = { version = "1.0.0" } <caret>
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test completion for value when caret inside inline table value`() = checkNoCompletion("""
        [dependencies]
        foo = { version = <caret> }
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test complete inside inline table`() = doSingleCompletion("""
        [dependencies]
        foo = { version = "<caret>" }
    """, """
        [dependencies]
        foo = { version = "1.0<caret>" }
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test complete inside inline table when another key exists`() = doSingleCompletion("""
        [dependencies]
        foo = { features = [], version = "<caret>" }
    """, """
        [dependencies]
        foo = { features = [], version = "1.0<caret>" }
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test complete specific dependency version`() = doSingleCompletion("""
        [dependencies.foo]
        version = "<caret>"
    """, """
        [dependencies.foo]
        version = "1.0<caret>"
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test complete specific dependency version with another existing key`() = doSingleCompletion("""
        [dependencies.foo]
        features = []
        version = "<caret>"
    """, """
        [dependencies.foo]
        features = []
        version = "1.0<caret>"
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test complete specific dependency version empty key`() = checkCompletion("version", """
        [dependencies.foo]
        <caret>
    """, """
        [dependencies.foo]
        version = "<caret>"
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test complete specific dependency version partial key`() = doSingleCompletion("""
        [dependencies.foo]
        ver<caret>
    """, """
        [dependencies.foo]
        version = "<caret>"
    """, "foo" to CargoRegistryCrate.of("1.0"))
}
