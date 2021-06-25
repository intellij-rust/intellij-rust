/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor
import org.rust.ide.MockBrowserLauncher
import org.rust.ide.lineMarkers.invokeNavigationHandler

@ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
class CargoCrateDocLineMarkerProviderTest : CargoTomlLineMarkerProviderTestBase() {
    fun `test standard version`() = doTest("""
        [dependencies]
        base64 = "0.8.0"  # - Open documentation for `base64@^0.8.0`

        [target.'cfg(unix)'.dependencies]
        base64 = "0.8.0"  # - Open documentation for `base64@^0.8.0`
    """, "https://docs.rs/base64/%5E0.8.0", "https://docs.rs/base64/%5E0.8.0")

    fun `test platform specific`() = doTest("""
        [target.'cfg(unix)'.dependencies]
        base64 = "0.8.0"  # - Open documentation for `base64@^0.8.0`
    """, "https://docs.rs/base64/%5E0.8.0")

    fun `test standard detailed`() = doTest("""
        [dependencies]
        jquery = { version = "1.0.2", optional = true }  # - Open documentation for `jquery@^1.0.2`
    """, "https://docs.rs/jquery/%5E1.0.2")

    fun `test standard dev`() = doTest("""
        [dev-dependencies]
        libc = "0.2.33"  # - Open documentation for `libc@^0.2.33`
    """, "https://docs.rs/libc/%5E0.2.33")

    fun `test standard build`() = doTest("""
        [build-dependencies]
        libc = "0.2.33"  # - Open documentation for `libc@^0.2.33`
    """, "https://docs.rs/libc/%5E0.2.33")

    fun `test specific crate`() = doTest("""
        [dependencies.clap]  # - Open documentation for `clap@^2.27.1`
        version = "2.27.1"
    """, "https://docs.rs/clap/%5E2.27.1")

    fun `test no link to doc`() = doTest("""
        [dependencies]
        hello_utils = { path = "hello_utils" }
    """)

    fun `test renamed dependencies`() = doTest("""
        [dependencies]
        config_rs = { package = "config", version = "0.9" }  # - Open documentation for `config@^0.9`
    """, "https://docs.rs/config/%5E0.9")

    fun `test unescape string literals`() = doTest("""
        [dependencies]
        serde = '1.0.104'  # - Open documentation for `serde@^1.0.104`
        serde_json = { version = '''1.0.104''' }  # - Open documentation for `serde_json@^1.0.104`
        serde_yaml_rs = { package = 'serde_yaml', version = '''0.8.11''' }  # - Open documentation for `serde_yaml@^0.8.11`
        serde_derive_rs = { package = "serde\u005Fderive", version ="1\u002E0\u002E104" }  # - Open documentation for `serde_derive@^1.0.104`
    """, "https://docs.rs/serde/%5E1.0.104",
        "https://docs.rs/serde_json/%5E1.0.104",
        "https://docs.rs/serde_yaml/%5E0.8.11",
        "https://docs.rs/serde_derive/%5E1.0.104"
    )

    fun `test empty version`() = doTest("""
        [dependencies]
        base64 = ""  # - Open documentation for `base64@*`
    """, "https://docs.rs/base64/*")

    fun `test exact version`() = doTest("""
        [dependencies]
        base64 = "=0.8.0"  # - Open documentation for `base64@=0.8.0`
    """, "https://docs.rs/base64/%3D0.8.0")

    private fun doTest(@Language("Toml") source: String, vararg expectedUrls: String) {
        doTestByText(source)

        val launcher = MockBrowserLauncher()
        launcher.replaceService(testRootDisposable)

        @Suppress("UNCHECKED_CAST")
        val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project) as List<LineMarkerInfo<PsiElement>>

        val actualUrls = mutableListOf<String>()
        for (marker in markers) {
            marker.invokeNavigationHandler(marker.element)
            actualUrls += launcher.lastUrl!!
        }
        assertEquals(expectedUrls.toList(), actualUrls)
    }
}
