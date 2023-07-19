/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.openapi.project.Project
import com.intellij.util.Urls
import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageId
import org.rust.lang.core.completion.RsChronoFormatCompletionTest.WithChronoDependencyRustProjectDescriptor
import java.nio.file.Paths
import org.rust.RustProjectDescriptorBase as RustProjectDescriptorBase1

@ProjectDescriptor(WithChronoDependencyRustProjectDescriptor::class)
class RsChronoFormatCompletionTest : RsCompletionTestBase() {
    @Language("Rust")
    private val CHRONO_CRATE_MOCK = """
    //- chrono/lib.rs
        struct NaiveTime{}
        impl NaiveTime{
                pub fn format(&self, non_fmt: &str, fmt: &str) {

                }

                pub fn format_assoc(non_fmt: &str, fmt: &str) {

                }
        }

        trait Format{}
"""

    fun `test format parameter`() = checkContainsCompletion(
        "%Y", """
        $CHRONO_CRATE_MOCK
        //- main.rs
        fn main() {
            NaiveTime::format_assoc("", "/*caret*/")
        }
    """.trimIndent()
    )

    fun `test non-format parameter`() = checkNotContainsCompletion(
        "%Y", """
        $CHRONO_CRATE_MOCK
        //- main.rs
        fn main() {
            NaiveTime::format_assoc("/*caret*/", "")
        }
    """.trimIndent()
    )

    fun `test format parameter method`() = checkContainsCompletion(
        "%Y", """
        $CHRONO_CRATE_MOCK
        //- main.rs
        fn main() {
            let nt = NaiveTime{};
            nt.format("", "/*caret*/")
        }
    """.trimIndent()
    )


    internal object WithChronoDependencyRustProjectDescriptor : RustProjectDescriptorBase1() {
        override fun createTestCargoWorkspace(project: Project, contentRoot: String): CargoWorkspace {
            val testPackage = testCargoPackage(contentRoot, name = "chrono")
            val packages = listOf(testPackage)
            return CargoWorkspace.deserialize(
                Paths.get("${Urls.newFromIdea(contentRoot).path}/workspace/Cargo.toml"),
                CargoWorkspaceData(
                    packages,
                    emptyMap(),
                    emptyMap(),
                    contentRoot
                ),
            )
        }
    }
}
