/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.openapi.module.Module
import com.intellij.util.Urls
import org.rust.ProjectDescriptor
import org.rust.RustProjectDescriptorBase
import org.rust.WithRustup
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspaceData
import java.nio.file.Paths

class RsPathCompletionTest : RsCompletionTestBase() {
    @ProjectDescriptor(WithWorkspaceAndStdLibProjectDescriptor::class)
    fun `test complete paths in path constructor root path 1`() = doSingleCompletionByFileTree("""
    //- crate-a/main.rs
        fn main() {
            std::path::Path::new("fo/*caret*/");
        }
    //- foo.rs
        pub struct Foo;
    """, """
        fn main() {
            std::path::Path::new("foo.rs/*caret*/");
        }
    """)

    @ProjectDescriptor(WithWorkspaceAndStdLibProjectDescriptor::class)
    fun `test complete paths in path constructor root path 2`() = doSingleCompletionByFileTree("""
    //- crate-a/main.rs
        fn main() {
            std::path::Path::new("crate-b/m/*caret*/");
        }
    //- crate-b/main.rs
        fn main() {}
    """, """
        fn main() {
            std::path::Path::new("crate-b/main.rs/*caret*/");
        }
    """)

    @ProjectDescriptor(WithWorkspaceAndStdLibProjectDescriptor::class)
    fun `test complete paths in path constructor imported path`() = doSingleCompletionByFileTree("""
    //- crate-a/main.rs
        use std::path::Path;
        fn main() {
            Path::new("fo/*caret*/");
        }
    //- foo.rs
        pub struct Foo;
    """, """
        use std::path::Path;
        fn main() {
            Path::new("foo.rs/*caret*/");
        }
    """)

    // enable once name resolution of <Foo as Trait>::function is fixed
    @ProjectDescriptor(WithWorkspaceAndStdLibProjectDescriptor::class)
    fun `test do not complete paths in path trait impl`() = checkNoCompletionByFileTree("""
    //- crate-a/main.rs
        use std::path::Path;
        trait Foo {
            fn new(x: &str) -> i32;
        }
        impl Foo for Path {
            fn new(x: &str) -> i32 {
                123
            }
        }
        fn main() {
            <Path as Foo>::new("fo/*caret*/");
        }
    //- foo.rs
        pub struct Foo;
    """)

    @ProjectDescriptor(WithWorkspaceAndStdLibProjectDescriptor::class)
    fun `test complete paths in pathbuf constructor`() = doSingleCompletionByFileTree("""
    //- crate-a/main.rs
        fn main() {
            std::path::PathBuf::from("fo/*caret*/");
        }
    //- foo.rs
        pub struct Foo;
    """, """
        fn main() {
            std::path::PathBuf::from("foo.rs/*caret*/");
        }
    """)

    @ProjectDescriptor(WithWorkspaceAndStdLibProjectDescriptor::class)
    fun `test complete paths in asref path`() = doSingleCompletionByFileTree("""
    //- crate-a/main.rs
        fn main() {
            std::fs::canonicalize("fo/*caret*/");
        }
    //- foo.rs
        pub struct Foo;
    """, """
        fn main() {
            std::fs::canonicalize("foo.rs/*caret*/");
        }
    """)

    @ProjectDescriptor(WithWorkspaceAndStdLibProjectDescriptor::class)
    fun `test complete paths in method call`() = doSingleCompletionByFileTree("""
    //- crate-a/main.rs
        struct Bar;
        impl Bar {
            fn foo<T: AsRef<std::path::Path>>(&self, path: T) {}
        }

        fn main() {
            Bar.foo("fo/*caret*/");
        }
    //- foo.rs
        pub struct Foo;
    """, """
        struct Bar;
        impl Bar {
            fn foo<T: AsRef<std::path::Path>>(&self, path: T) {}
        }

        fn main() {
            Bar.foo("foo.rs/*caret*/");
        }
    """)

    @ProjectDescriptor(WithWorkspaceAndStdLibProjectDescriptor::class)
    fun `test complete paths in ufcs call`() = doSingleCompletionByFileTree("""
    //- crate-a/main.rs
        struct Bar;
        impl Bar {
            fn foo<T: AsRef<std::path::Path>>(&self, path: T) {}
        }

        fn main() {
            Bar::foo(&Bar, "fo/*caret*/");
        }
    //- foo.rs
        pub struct Foo;
    """, """
        struct Bar;
        impl Bar {
            fn foo<T: AsRef<std::path::Path>>(&self, path: T) {}
        }

        fn main() {
            Bar::foo(&Bar, "foo.rs/*caret*/");
        }
    """)

    @ProjectDescriptor(WithWorkspaceAndStdLibProjectDescriptor::class)
    fun `test do not complete paths in self parameter of ufcs call`() = checkNoCompletionByFileTree("""
    //- crate-a/main.rs
        struct Bar;
        impl Bar {
            fn foo<T: AsRef<std::path::Path>>(&self, path: T) {}
        }

        fn main() {
            Bar::foo("fo/*caret*/");
        }
    //- foo.rs
        pub struct Foo;
    """)

    @ProjectDescriptor(WithWorkspaceAndStdLibProjectDescriptor::class)
    fun `test complete paths in impl asref`() = doSingleCompletionByFileTree("""
    //- crate-a/main.rs
        fn foo(path: impl AsRef<std::path::Path>) {}

        fn main() {
            foo("fo/*caret*/");
        }
    //- foo.rs
        pub struct Foo;
    """, """
        fn foo(path: impl AsRef<std::path::Path>) {}

        fn main() {
            foo("foo.rs/*caret*/");
        }
    """)

    fun `test do not complete paths in string literal`() = checkNoCompletionByFileTree("""
    //- main.rs
        fn main() {
            let s = "fo/*caret*/";
        }
    //- foo.rs
        pub struct Foo;
    """)

    fun `test complete paths in include macro`() = doSingleCompletionByFileTree("""
    //- main.rs
        include!("fo/*caret*/");
    //- foo.rs
        pub struct Foo;
    """, """
        include!("foo.rs/*caret*/");
    """)

    fun `test complete path in path attribute on mod decl`() = doSingleCompletionByFileTree("""
    //- main.rs
        #[path="b/*caret*/"]
        mod foo;
    //- bar.rs
        fn bar() {}
    """, """
        #[path="bar.rs/*caret*/"]
        mod foo;
    """)

    fun `test complete rust file path in path attribute`() = doSingleCompletionByFileTree("""
    //- main.rs
        #[path="b/*caret*/"]
        mod foo;
    //- bar.rs
        fn bar() {}
    //- baz.txt
        // some text
    """, """
        #[path="bar.rs/*caret*/"]
        mod foo;
    """)

    fun `test complete path in path attribute on mod`() = doSingleCompletionByFileTree("""
    //- main.rs
        #[path="ba/*caret*/"]
        mod foo {
        }
    //- baz/bar.rs
        fn bar() {}
    """, """
        #[path="baz/*caret*/"]
        mod foo {
        }
    """)

    fun `test complete path in path attribute on inner mod decl`() = doSingleCompletionByFileTree("""
    //- main.rs
        #[path="baz"]
        mod foo {
            #[path="ba/*caret*/"]
            mod qqq;
        }
    //- baz/bar.rs
        fn bar() {}
    """, """
        #[path="baz"]
        mod foo {
            #[path="bar.rs/*caret*/"]
            mod qqq;
        }
    """)

    fun `test complete path in path attribute under cfg_attr on mod decl`() = doSingleCompletionByFileTree("""
    //- main.rs
        #[cfg_attr(unix, path="b/*caret*/")]
        mod foo;
    //- bar.rs
        fn bar() {}
    """, """
        #[cfg_attr(unix, path="bar.rs/*caret*/")]
        mod foo;
    """)
}

private object WithWorkspaceProjectDescriptor : RustProjectDescriptorBase() {
    override fun testCargoProject(module: Module, contentRoot: String): CargoWorkspace {
        val crateA = testCargoPackage("$contentRoot/crate-a", name="crate-a")
        val crateB = testCargoPackage("$contentRoot/crate-b", name="crate-b")

        val packages = listOf(
            crateA,
            crateB
        )
        return CargoWorkspace.deserialize(
            Paths.get("${Urls.newFromIdea(contentRoot).path}/workspace/Cargo.toml"),
            CargoWorkspaceData(packages, emptyMap(), emptyMap(), contentRoot), CfgOptions.DEFAULT)
    }
}

object WithWorkspaceAndStdLibProjectDescriptor : WithRustup(WithWorkspaceProjectDescriptor)
