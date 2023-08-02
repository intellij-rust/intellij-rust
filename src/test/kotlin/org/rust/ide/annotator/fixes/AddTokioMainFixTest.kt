/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.openapi.project.Project
import com.intellij.util.Urls
import org.rust.MockRustcVersion
import org.rust.ProjectDescriptor
import org.rust.RustProjectDescriptorBase
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageId
import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator
import org.rust.ide.inspections.RsAsyncMainFunctionInspection
import org.rust.ide.inspections.RsInspectionsTestBase
import java.nio.file.Paths

class AddTokioMainFixTest : RsInspectionsTestBase(RsAsyncMainFunctionInspection::class) {
    fun `test fix add tokio main`() = checkFixByFileTree("Add `#[tokio::main]`", """
    //- main.rs
        /*error descr="`main` function is not allowed to be `async` [E0752]"*/async/*caret*//*error**/ fn main() {}
    """, """
    //- main.rs
        #[tokio::main]
        async fn main() {}
    """)

    fun `test fix add tokio with keyword`() = checkFixByFileTree("Add `#[tokio::main]`", """
    //- main.rs
        pub /*error descr="`main` function is not allowed to be `async` [E0752]"*/async/*caret*//*error**/ fn main() {}
    """, """
    //- main.rs
        #[tokio::main]
        pub async fn main() {}
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test fix add tokio with start`() = checkFixByFileTree("Add `#[tokio::main]`", """
    //- main.rs
        #![feature(start)]

        #[start]
        /*error descr="`start` function is not allowed to be `async` [E0752]"*/async/*caret*//*error**/ fn start() {}
    """, """
    //- main.rs
        #![feature(start)]

        #[tokio::main]
        #[start]
        async fn start() {}
    """)

    fun `test fix add tokio dependency`() = checkFixByFileTree("Add `#[tokio::main]`", """
    //- main.rs
        /*error descr="`main` function is not allowed to be `async` [E0752]"*/async/*caret*//*error**/ fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"
    """, """
    //- main.rs
        #[tokio::main]
        async fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"
        [dependencies]
        tokio = { version = "1.0.0", features = ["rt", "rt-multi-thread", "macros"] }
    """)

    fun `test fix add tokio dependency with empty dependencies`() = checkFixByFileTree("Add `#[tokio::main]`", """
    //- main.rs
        /*error descr="`main` function is not allowed to be `async` [E0752]"*/async/*caret*//*error**/ fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies]
    """, """
    //- main.rs
        #[tokio::main]
        async fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies]
        tokio = { version = "1.0.0", features = ["rt", "rt-multi-thread", "macros"] }
    """)

    fun `test fix add tokio dependency with other dependencies`() = checkFixByFileTree("Add `#[tokio::main]`", """
    //- main.rs
        /*error descr="`main` function is not allowed to be `async` [E0752]"*/async/*caret*//*error**/ fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies]
        rand = "1.0.0"
    """, """
    //- main.rs
        #[tokio::main]
        async fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies]
        rand = "1.0.0"
        tokio = { version = "1.0.0", features = ["rt", "rt-multi-thread", "macros"] }
    """)

    fun `test fix add tokio features if dep exist`() = checkFixByFileTree("Add `#[tokio::main]`", """
    //- main.rs
        /*error descr="`main` function is not allowed to be `async` [E0752]"*/async/*caret*//*error**/ fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies]
        tokio = "1.2.9"
    """, """
    //- main.rs
        #[tokio::main]
        async fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies]
        tokio = { version = "1.2.9", features = ["rt", "rt-multi-thread", "macros"] }
    """)

    fun `test fix add missing tokio features`() = checkFixByFileTree("Add `#[tokio::main]`", """
    //- main.rs
        /*error descr="`main` function is not allowed to be `async` [E0752]"*/async/*caret*//*error**/ fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies]
        tokio = { version = "1.2.9", features = ["rt", "rt-multi-thread"] }
    """, """
    //- main.rs
        #[tokio::main]
        async fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies]
        tokio = { version = "1.2.9", features = ["rt", "rt-multi-thread", "macros"] }
    """)

    fun `test fix add missing tokio features preserves old order`() = checkFixByFileTree("Add `#[tokio::main]`", """
    //- main.rs
        /*error descr="`main` function is not allowed to be `async` [E0752]"*/async/*caret*//*error**/ fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies]
        tokio = { version = "1.2.9", features = ["fs", "rt-multi-thread", "rt"] }
    """, """
    //- main.rs
        #[tokio::main]
        async fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies]
        tokio = { version = "1.2.9", features = ["fs", "rt-multi-thread", "rt", "macros"] }
    """)

    fun `test fix add missing tokio features with inline value`() = checkFixByFileTree("Add `#[tokio::main]`", """
    //- main.rs
        /*error descr="`main` function is not allowed to be `async` [E0752]"*/async/*caret*//*error**/ fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies.tokio]
        version = "1.0.0"
        features = ["fs", "rt"]
    """, """
    //- main.rs
        #[tokio::main]
        async fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies.tokio]
        version = "1.0.0"
        features = ["fs", "rt", "rt-multi-thread", "macros"]
    """)

    fun `test fix add missing tokio features with inline value with name`() = checkFixByFileTree("Add `#[tokio::main]`", """
    //- main.rs
        /*error descr="`main` function is not allowed to be `async` [E0752]"*/async/*caret*//*error**/ fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies.xxx]
        name = "tokio"
        version = "1.0.0"
        features = ["fs", "rt"]
    """, """
    //- main.rs
        #[tokio::main]
        async fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies.xxx]
        name = "tokio"
        version = "1.0.0"
        features = ["fs", "rt", "rt-multi-thread", "macros"]
    """)

    fun `test fix add missing tokio features with named dependency`() = checkFixByFileTree("Add `#[tokio::main]`", """
    //- main.rs
        /*error descr="`main` function is not allowed to be `async` [E0752]"*/async/*caret*//*error**/ fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies]
        xxx = { name = "tokio", version = "1.2.9", features = ["fs"] }
    """, """
    //- main.rs
        #[tokio::main]
        async fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies]
        xxx = { name = "tokio", version = "1.2.9", features = ["fs", "rt", "rt-multi-thread", "macros"] }
    """)

    fun `test fix add missing tokio features with named dependencies`() = checkFixByFileTree("Add `#[tokio::main]`", """
    //- main.rs
        /*error descr="`main` function is not allowed to be `async` [E0752]"*/async/*caret*//*error**/ fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies]
        tokio = { name = "serde", version = "0.0.0" }
        serde = { name = "tokio", version = "1.2.9" }
    """, """
    //- main.rs
        #[tokio::main]
        async fn main() {}
    //- Cargo.toml
        [package]
        name = "rustsandbox"
        version = "0.1.0"
        edition = "2021"

        [dependencies]
        tokio = { name = "serde", version = "0.0.0" }
        serde = { name = "tokio", version = "1.2.9", features = ["rt", "rt-multi-thread", "macros"] }
    """)
}

class AddTokioMainFix2Test : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test await inside non-async function`() = checkFixByFileTree("Add `#[tokio::main]`", """
    //- main.rs
        fn main() {
            x./*error descr="`await` is only allowed inside `async` functions and blocks [E0728]"*/await/*caret*//*error**/;
        }
    """, """
    //- main.rs
        #[tokio::main]
        async fn main() {
            x.await;
        }
    """)
}
