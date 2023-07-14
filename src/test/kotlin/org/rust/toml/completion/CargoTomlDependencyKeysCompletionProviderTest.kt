/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.rust.RsJUnit4ParameterizedTestRunner
import org.rust.toml.crates.local.CargoRegistryCrate

@RunWith(RsJUnit4ParameterizedTestRunner::class)
@Parameterized.UseParametersRunnerFactory(RsJUnit4ParameterizedTestRunner.RsRunnerForParameters.Factory::class)
class CargoTomlDependencyKeysCompletionProviderTest : LocalCargoTomlCompletionTestBase() {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{1}")
        fun data(): Iterable<Array<String>> {
            return listOf(
                arrayOf("b", "branch", "\"", "\""),
                arrayOf("d", "default-features", "", ""),
                arrayOf("f", "features", "[", "]"),
                arrayOf("g", "git", "\"", "\""),
                arrayOf("o", "optional", "", ""),
                arrayOf("pac", "package", "\"", "\""),
                arrayOf("pat", "path", "\"", "\""),
                arrayOf("reg", "registry", "\"", "\""),
                arrayOf("rev", "rev", "\"", "\""),
                arrayOf("t", "tag", "\"", "\"")
            )
        }
    }

    @Parameterized.Parameter(0)
    lateinit var prefix: String

    @Parameterized.Parameter(1)
    lateinit var keyName: String

    @Parameterized.Parameter(2)
    lateinit var valueStart: String

    @Parameterized.Parameter(3)
    lateinit var valueEnd: String

    fun `test complete inline table`() = doFirstCompletion("""
        [dependencies]
        foo = { ${prefix}<caret> }
    """, """
        [dependencies]
        foo = { $keyName = $valueStart<caret>$valueEnd }
    """, "foo" to CargoRegistryCrate.of("0.1.0"))

    fun `test complete inline table when another key already exists`() = doFirstCompletion("""
        [dependencies]
        foo = { features = [], ${prefix}<caret> }
    """, """
        [dependencies]
        foo = { features = [], $keyName = $valueStart<caret>$valueEnd }
    """, "foo" to CargoRegistryCrate.of("0.1.0"))

    fun `test complete table`() = doFirstCompletion("""
        [dependencies.foo]
        ${prefix}<caret>
    """, """
        [dependencies.foo]
        $keyName = $valueStart<caret>$valueEnd
    """, "foo" to CargoRegistryCrate.of("0.1.0"))

    fun `test complete when another key already exists`() = doFirstCompletion("""
        [dependencies.foo]
        version = "0.1.0"
        ${prefix}<caret>
    """, """
        [dependencies.foo]
        version = "0.1.0"
        $keyName = $valueStart<caret>$valueEnd
    """, "foo" to CargoRegistryCrate.of("0.1.0"))

    fun `test complete when another key already exists after`() = doFirstCompletion("""
        [dependencies.foo]
        ${prefix}<caret>
        version = "0.1.0"
    """, """
        [dependencies.foo]
        $keyName = $valueStart<caret>$valueEnd
        version = "0.1.0"
    """, "foo" to CargoRegistryCrate.of("0.1.0"))

    fun `test complete inline table when value already exists`() = doFirstCompletion("""
        [dependencies]
        foo = { ${prefix}<caret> = $valueStart$valueEnd }
    """, """
        [dependencies]
        foo = { $keyName = $valueStart<caret>$valueEnd }
    """, "foo" to CargoRegistryCrate.of("0.1.0"))

    fun `test complete table when value already exists`() = doFirstCompletion("""
        [dependencies.foo]
        ${prefix}<caret> = $valueStart$valueEnd
    """, """
        [dependencies.foo]
        $keyName = $valueStart<caret>$valueEnd
    """, "foo" to CargoRegistryCrate.of("0.1.0"))

    fun `test no completion in inline table value`() = checkNoCompletion("""
        [dependencies]
        foo = { key = "${prefix}<caret>" }
    """, "foo" to CargoRegistryCrate.of("0.1.0"))

    fun `test no completion in property value`() = checkNoCompletion("""
        [dependencies.foo]
        key = ${prefix}<caret>
    """, "foo" to CargoRegistryCrate.of("0.1.0"))

    fun `test no completion for version in values`() = checkNotContainsCompletion("0.1.0", """
        [dependencies.foo]
        $keyName = "<caret>"
    """, "foo" to CargoRegistryCrate.of("0.1.0"))
}
