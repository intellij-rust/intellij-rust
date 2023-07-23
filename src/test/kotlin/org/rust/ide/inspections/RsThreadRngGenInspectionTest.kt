/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.openapi.project.Project
import com.intellij.util.Urls
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.rust.ProjectDescriptor
import org.rust.RustProjectDescriptorBase
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageId
import org.rust.ide.annotator.ExplicitPreview
import java.nio.file.Paths

@ProjectDescriptor(WithRandDependencyRustProjectDescriptor::class)
class RsThreadRngGenInspectionTest : RsInspectionsTestBase(RsThreadRngGenInspection::class) {
    fun `test warning simple`() = checkByFileTree("""
        $RAND_CRATE_MOCK
    //- main.rs
        use rand::*;
        fn main() {/*caret*/
            let x: i32 = /*weak_warning descr="Can be replaced with 'random()'"*/thread_rng().gen()/*weak_warning**/;
        }
    """, checkWeakWarn = true)

    fun `test fix simple`() = checkFixByFileTree("Replace with `random()`", """
        $RAND_CRATE_MOCK
    //- main.rs
        use rand::*;
        fn main() {
            let x: i32 = thread_rng().gen()/*caret*/;
        }
    """, """
    //- main.rs
        use rand::*;
        fn main() {
            let x: i32 = random();
        }
    """)

    @Test(expected = Throwable::class)
    fun `test fix separate imports`() = checkFixByFileTree("Replace with `random()`", """
        $RAND_CRATE_MOCK
    //- main.rs
        use rand::{thread_rng, Rng};
        fn main() {
            let x: i32 = thread_rng().gen()/*caret*/;
        }
    """, """
    //- main.rs
        use rand::random;
        fn main() {
            let x: i32 = random();
        }
    """, preview = ExplicitPreview("""
        use rand::{thread_rng, Rng};
        fn main() {
            let x: i32 = random();
        }
    """))

    @Test(expected = Throwable::class)
    fun `test fix separate imports with extra`() = checkFixByFileTree("Replace with `random()`", """
        $RAND_CRATE_MOCK
    //- main.rs
        use rand::{thread_rng, Rng, Error};
        fn main() {
            let x: i32 = thread_rng().gen()/*caret*/;
        }
    """, """
    //- main.rs
        use rand::{Error, random};
        fn main() {
            let x: i32 = random();
        }
    """, preview = ExplicitPreview("""
        use rand::{thread_rng, Rng, Error};
        fn main() {
            let x: i32 = random();
        }
    """))

    @Test(expected = Throwable::class)
    fun `test fix with weird import`() = checkFixByFileTree("Replace with `random()`", """
        $RAND_CRATE_MOCK
    //- main.rs
        use rand::*;
        pub mod foo {
            pub use rand::thread_rng as tr;
        }
        use foo::tr;
        fn main() {
            let x: i32 = tr().gen()/*caret*/;
        }
    """, """
    //- main.rs
        use rand::*;
        pub mod foo {
            pub use rand::thread_rng as tr;
        }

        fn main() {
            let x: i32 = random();
        }
    """, preview = ExplicitPreview("""
        use rand::*;
        pub mod foo {
            pub use rand::thread_rng as tr;
        }
        use foo::tr;
        fn main() {
            let x: i32 = random();
        }
    """))

    @Test(expected = Throwable::class)
    fun `test fix with another random in scope`() = checkFixByFileTree(
        "Replace with `rand::random()`", """
        $RAND_CRATE_MOCK
    //- main.rs
        use rand::{thread_rng, Rng};
        fn random() -> i32 { unimplemented!() }
        fn main() {
            let x: i32 = thread_rng().gen()/*caret*/;
        }
    """, """
    //- main.rs
        fn random() -> i32 { unimplemented!() }
        fn main() {
            let x: i32 = rand::random();
        }
    """, preview = ExplicitPreview("""
        use rand::{thread_rng, Rng};
        fn random() -> i32 { unimplemented!() }
        fn main() {
            let x: i32 = rand::random();
        }
    """))

    @Test(expected = Throwable::class)
    fun `test fix does not change imports in another mod`() = checkFixByFileTree(
    "Replace with `random()`", """
        $RAND_CRATE_MOCK
    //- main.rs
        use rand::{thread_rng, Rng};
        mod foo {
            use rand::{thread_rng, Rng};
            fn foo() {
                let x: i32 = thread_rng().gen()/*caret*/;
            }
        }
    """, """
    //- main.rs
        use rand::{thread_rng, Rng};
        mod foo {
            use rand::random;
            fn foo() {
                let x: i32 = random()/*caret*/;
            }
        }
    """, preview = ExplicitPreview("""
        use rand::{thread_rng, Rng};
        mod foo {
            use rand::{thread_rng, Rng};
            fn foo() {
                let x: i32 = random();
            }
        }
    """))

    fun `test warning with type arguments`() = checkByFileTree("""
        $RAND_CRATE_MOCK
    //- main.rs
        use rand::*;
        fn main() {/*caret*/
            let x = /*weak_warning descr="Can be replaced with 'random::<i32>()'"*/thread_rng().gen::<i32>()/*weak_warning**/;
        }
    """, checkWeakWarn = true)

    fun `test fix with type argument`() = checkFixByFileTree("Replace with `random::<i32>()`", """
        $RAND_CRATE_MOCK
    //- main.rs
        use rand::*;
        fn main() {
            let x = thread_rng().gen::<i32>()/*caret*/;
        }
    """, """
    //- main.rs
        use rand::*;
        fn main() {
            let x = random::<i32>();
        }
    """)

    fun `test warning missing when wrong functions`() = checkByFileTree("""
        $RAND_CRATE_MOCK
    //- main.rs
        struct S;
        fn thread_rng() -> S { S }
        impl S {
            fn gen() -> i32 { 0 }
        }
        fn main() {/*caret*/
            let x: i32 = thread_rng().gen();
        }
    """, checkWeakWarn = true)
}

@Language("Rust")
private const val RAND_CRATE_MOCK = """
    //- rand/lib.rs
        pub mod rand_core {
            pub trait RngCore {}
            pub struct Error;
        }

        pub use crate::rngs::thread::*;
        pub use crate::rand_core::{RngCore, Error};
        pub use crate::rng::Rng;

        pub fn random<T>() -> T { todo!() }
        pub mod rngs {
            use crate::rand::RngCore;

            pub mod thread {
                use crate::rand_core::RngCore;

                pub struct ThreadRng;
                pub fn thread_rng() -> ThreadRng { todo!() }
                impl RngCore for ThreadRng {}
            }
        }

        mod rng {
            pub trait Rng: RngCore {
                fn gen<T>(&mut self) -> T { todo!() }
            }
            impl<R: RngCore + ?Sized> Rng for R {}
        }
"""

internal object WithRandDependencyRustProjectDescriptor : RustProjectDescriptorBase() {
    override fun createTestCargoWorkspace(project: Project, contentRoot: String): CargoWorkspace {
        val rand = externalPackage("$contentRoot/rand", "lib.rs", "rand")
        val testPackage = testCargoPackage(contentRoot)
        val packages = listOf(testPackage, rand)
        return CargoWorkspace.deserialize(
            Paths.get("${Urls.newFromIdea(contentRoot).path}/workspace/Cargo.toml"),
            CargoWorkspaceData(
                packages,
                mapOf(testPackage.id to setOf(dep(rand.id))),
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
