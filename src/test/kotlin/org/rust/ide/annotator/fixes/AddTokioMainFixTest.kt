/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.openapi.project.Project
import com.intellij.util.Urls
import org.rust.ProjectDescriptor
import org.rust.RustProjectDescriptorBase
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageId
import org.rust.ide.inspections.RsAsyncMainFunctionInspection
import org.rust.ide.inspections.RsInspectionsTestBase
import java.nio.file.Paths

@ProjectDescriptor(WithTokioDependencyRustProjectDescriptor::class)
class AddTokioMainFixTest : RsInspectionsTestBase(RsAsyncMainFunctionInspection::class) {
    fun `test fix add tokio main`() = checkFixByFileTree("Add `#[tokio::main]`", """
        $TOKIO_CRATE_MOCK
    //- main.rs
        /*error descr="`main` function is not allowed to be `async` [E0752]"*/async/*caret*//*error**/ fn main() {}
    """, """
    //- main.rs
        #[tokio::main]
        async fn main() {}
    """)

    fun `test fix add tokio with keyword`() = checkFixByFileTree("Add `#[tokio::main]`", """
        $TOKIO_CRATE_MOCK
    //- main.rs
        pub /*error descr="`main` function is not allowed to be `async` [E0752]"*/async/*caret*//*error**/ fn main() {}
    """, """
    //- main.rs
        #[tokio::main]
        pub async fn main() {}
    """)
}

private const val TOKIO_CRATE_MOCK = """
    //- tokio/lib.rs
        pub mod tokio {
            pub struct Error;
        }

        pub fun main() {}
"""
internal object WithTokioDependencyRustProjectDescriptor : RustProjectDescriptorBase() {
    override fun createTestCargoWorkspace(project: Project, contentRoot: String): CargoWorkspace {
        val tokio = externalPackage("$contentRoot/tokio", "lib.rs", "tokio")
        val testPackage = testCargoPackage(contentRoot)
        val packages = listOf(testPackage, tokio)
        return CargoWorkspace.deserialize(
            Paths.get("${Urls.newFromIdea(contentRoot).path}/workspace/Cargo.toml"),
            CargoWorkspaceData(
                packages,
                mapOf(testPackage.id to setOf(dep(tokio.id))),
                emptyMap(),
                contentRoot
            ),
        )
    }

    private fun dep(id: PackageId): CargoWorkspaceData.Dependency = CargoWorkspaceData.Dependency(
        id = id,
        name = null,
        depKinds = listOf(CargoWorkspace.DepKindInfo(CargoWorkspace.DepKind.Normal))
    )
}
