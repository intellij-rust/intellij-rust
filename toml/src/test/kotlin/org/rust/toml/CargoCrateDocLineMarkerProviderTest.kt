/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor

@ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
class CargoCrateDocLineMarkerProviderTest : CargoTomlLineMarkerProviderTestBase() {
    fun `test standard version`() = doTestByText("""
        [dependencies]
        base64 = "0.8.0"  # - Open documentation for `base64@^0.8.0`

        [target.'cfg(unix)'.dependencies]
        base64 = "0.8.0"  # - Open documentation for `base64@^0.8.0`
    """)

    fun `test platform specific`() = doTestByText("""
        [target.'cfg(unix)'.dependencies]
        base64 = "0.8.0"  # - Open documentation for `base64@^0.8.0`
    """)

    fun `test standard detailed`() = doTestByText("""
        [dependencies]
        jquery = { version = "1.0.2", optional = true }  # - Open documentation for `jquery@^1.0.2`
    """)

    fun `test standard dev`() = doTestByText("""
        [dev-dependencies]
        libc = "0.2.33"  # - Open documentation for `libc@^0.2.33`
    """)

    fun `test standard build`() = doTestByText("""
        [build-dependencies]
        libc = "0.2.33"  # - Open documentation for `libc@^0.2.33`
    """)

    fun `test specific crate`() = doTestByText("""
        [dependencies.clap]  # - Open documentation for `clap@^2.27.1`
        version = "2.27.1"
    """)

    fun `test no link to doc`() = doTestByText("""
        [dependencies]
        hello_utils = { path = "hello_utils" }
    """)

    fun `test renamed dependencies`() = doTestByText("""
        [dependencies]
        config_rs = { package = "config", version = "0.9" }  # - Open documentation for `config@^0.9`
    """)

    fun `test unescape string literals`() = doTestByText("""
        [dependencies]
        serde = '1.0.104'  # - Open documentation for `serde@^1.0.104`
        serde_json = { version = '''1.0.104''' }  # - Open documentation for `serde_json@^1.0.104`
        serde_yaml_rs = { package = 'serde_yaml', version = '''0.8.11''' }  # - Open documentation for `serde_yaml@^0.8.11`
        serde_derive_rs = { package = "serde\u005Fderive", version ="1\u002E0\u002E104" }  # - Open documentation for `serde_derive@^1.0.104`
    """)

    fun `test empty version`() = doTestByText("""
        [dependencies]
        base64 = ""  # - Open documentation for `base64@*`
    """)

    fun `test exact version`() = doTestByText("""
        [dependencies]
        base64 = "=0.8.0"  # - Open documentation for `base64@=0.8.0`
    """.trimIndent())
}
