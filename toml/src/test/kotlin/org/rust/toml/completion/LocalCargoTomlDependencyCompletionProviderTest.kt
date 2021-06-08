/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import org.rust.toml.crates.local.CargoRegistryCrate

class LocalCargoTomlDependencyCompletionProviderTest : LocalCargoTomlCompletionTestBase() {
    fun `test basic completion`() = doSingleCompletion("""
        [dependencies]
        fo<caret>
    """, """
        [dependencies]
        foo = "<caret>"
    """,
        "foo" to CargoRegistryCrate.of("1.0.0"),
        "bar" to CargoRegistryCrate.of("1.0.0")
    )

    fun `test no completion`() = checkNoCompletion("""
        [dependencies]
        fo<caret>
    """, "bar" to CargoRegistryCrate.of("1.0.0"))

    fun `test complete with hyphen-underscore disambiguation`() = doSingleCompletion("""
        [dependencies]
        foo-<caret>
    """, """
        [dependencies]
        foo_bar = "<caret>"
    """, "foo_bar" to CargoRegistryCrate.of("1.0.0"))

    fun `test complete by subwords`() = doSingleCompletion("""
        [dependencies]
        f-ba<caret>
    """, """
        [dependencies]
        foo_bar = "<caret>"
    """, "foo_bar" to CargoRegistryCrate.of("1.0.0"))

    fun `test complete with complex dependency head`() = doSingleCompletion("""
        [target.'cfg(windows)'.dev-dependencies]
        f<caret>
    """, """
        [target.'cfg(windows)'.dev-dependencies]
        foo = "<caret>"
    """, "foo" to CargoRegistryCrate.of("1.0"))

    fun `test complete specific dependency header partial key`() = doSingleCompletion("""
        [dependencies.f<caret>]
    """, """
        [dependencies.foo]
        version = "<caret>"
    """,
        "foo" to CargoRegistryCrate.of("1.0"),
        "bar" to CargoRegistryCrate.of("2.0")
    )

    fun `test complete specific dependency header with existing version`() = doSingleCompletion("""
        [dependencies.f<caret>]
        version = "1.0"
    """, """
        [dependencies.foo<caret>]
        version = "1.0"
    """,
        "foo" to CargoRegistryCrate.of("1.0")
    )

    fun `test complete specific dependency header with existing keys`() = doSingleCompletion("""
        [dependencies.f<caret>]
        default-features = false
    """, """
        [dependencies.foo<caret>]
        default-features = false
    """,
        "foo" to CargoRegistryCrate.of("1.0")
    )
}
