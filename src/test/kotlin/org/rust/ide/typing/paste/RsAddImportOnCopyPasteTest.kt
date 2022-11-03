/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.paste

import com.intellij.openapi.actionSystem.IdeActions
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.fileTreeFromText
import org.rust.ide.settings.RsCodeInsightSettings

class RsAddImportOnCopyPasteTest : RsTestBase() {
    fun `test type reference`() = doCopyPasteTest("""
        //- lib.rs
        mod foo {
            pub struct S;
        }

        mod bar {
            use crate::foo::S;

            <selection>fn fun(_: S) {}</selection>
        }

        mod baz {
            /*caret*/
        }
    """, """
        //- lib.rs
        mod foo {
            pub struct S;
        }

        mod bar {
            use crate::foo::S;

            fn fun(_: S) {}
        }

        mod baz {
            use crate::foo::S;

            fn fun(_: S) {}
        }
    """)

    fun `test expression context`() = doCopyPasteTest("""
        //- lib.rs
        mod foo {
            pub struct S;

            impl S {
                pub fn new() -> Self { S }
            }
        }

        mod bar {
            use crate::foo::S;

            <selection>fn fun() {
                let _ = S::new();
            }</selection>
        }

        mod baz {
            /*caret*/
        }
    """, """
        //- lib.rs
        mod foo {
            pub struct S;

            impl S {
                pub fn new() -> Self { S }
            }
        }

        mod bar {
            use crate::foo::S;

            fn fun() {
                let _ = S::new();
            }
        }

        mod baz {
            use crate::foo::S;

            fn fun() {
                let _ = S::new();
            }
        }
    """)

    fun `test method call import trait`() = doCopyPasteTest("""
        //- lib.rs
        mod foo {
            pub trait Trait {
                fn foo(&self) {}
            }

            impl Trait for () {
                fn foo(&self) {}
            }
        }

        mod bar {
            use crate::foo::Trait;

            <selection>fn fun() {
                ().foo();
            }</selection>
        }

        mod baz {
            /*caret*/
        }
    """, """
        //- lib.rs
        mod foo {
            pub trait Trait {
                fn foo(&self) {}
            }

            impl Trait for () {
                fn foo(&self) {}
            }
        }

        mod bar {
            use crate::foo::Trait;

            fn fun() {
                ().foo();
            }
        }

        mod baz {
            use crate::foo::Trait;

            fn fun() {
                ().foo();
            }
        }
    """)

    fun `test import from multiple elements`() = doCopyPasteTest("""
        //- lib.rs
        mod foo {
            pub struct S1;
            pub struct S2;
            pub struct S3;
        }

        mod bar {
            use crate::foo::S1;
            use crate::foo::S2;
            use crate::foo::S3;

            <selection>fn fun1(_: S1) {}
            fn fun2(_: S2) {}
            fn fun3(_: S3) {}</selection>
        }

        mod baz {
            /*caret*/
        }
    """, """
        //- lib.rs
        mod foo {
            pub struct S1;
            pub struct S2;
            pub struct S3;
        }

        mod bar {
            use crate::foo::S1;
            use crate::foo::S2;
            use crate::foo::S3;

            fn fun1(_: S1) {}
            fn fun2(_: S2) {}
            fn fun3(_: S3) {}
        }

        mod baz {
            use crate::foo::{S1, S2, S3};

            fn fun1(_: S1) {}
            fn fun2(_: S2) {}
            fn fun3(_: S3) {}
        }
    """)

    fun `test import from other file`() = doCopyPasteTest("""
        //- lib.rs
        mod foo;
        mod bar;

        //- foo.rs
        pub struct S;

        <selection>fn fun(_: S) {}</selection>

        //- bar.rs
        /*caret*/
    """, """
        //- lib.rs
        mod foo;
        mod bar;

        //- foo.rs
        pub struct S;

        fn fun(_: S) {}

        //- bar.rs
        use crate::foo::S;

        fn fun(_: S) {}
    """)

    fun `test ambiguous import 1`() = doCopyPasteTest("""
        //- lib.rs
        mod foo1 {
            pub struct S;
        }
        mod foo2 {
            pub struct S;
        }

        mod bar {
            use crate::foo1::S;

            <selection>fn fun(_: S) {}</selection>
        }

        mod baz {
            /*caret*/
        }
    """, """
        //- lib.rs
        mod foo1 {
            pub struct S;
        }
        mod foo2 {
            pub struct S;
        }

        mod bar {
            use crate::foo1::S;

            fn fun(_: S) {}
        }

        mod baz {
            use crate::foo1::S;

            fn fun(_: S) {}
        }
    """)

    fun `test ambiguous import 2`() = doCopyPasteTest("""
        //- lib.rs
        mod foo1 {
            pub struct S;
        }
        mod foo2 {
            pub struct S;
        }

        mod bar {
            use crate::foo2::S;

            <selection>fn fun(_: S) {}</selection>
        }

        mod baz {
            /*caret*/
        }
    """, """
        //- lib.rs
        mod foo1 {
            pub struct S;
        }
        mod foo2 {
            pub struct S;
        }

        mod bar {
            use crate::foo2::S;

            fn fun(_: S) {}
        }

        mod baz {
            use crate::foo2::S;

            fn fun(_: S) {}
        }
    """)

    fun `test ambiguous trait import 1`() = doCopyPasteTest("""
        //- lib.rs
        mod foo {
            pub trait Trait1 {
                fn foo(&self) {}
            }
            impl Trait1 for () {
                fn foo(&self) {}
            }

            pub trait Trait2 {
                fn foo(&self) {}
            }
            impl Trait2 for () {
                fn foo(&self) {}
            }
        }

        mod bar {
            use crate::foo::Trait1;

            <selection>fn fun() {
                ().foo();
            }</selection>
        }

        mod baz {
            /*caret*/
        }
    """, """
        //- lib.rs
        mod foo {
            pub trait Trait1 {
                fn foo(&self) {}
            }
            impl Trait1 for () {
                fn foo(&self) {}
            }

            pub trait Trait2 {
                fn foo(&self) {}
            }
            impl Trait2 for () {
                fn foo(&self) {}
            }
        }

        mod bar {
            use crate::foo::Trait1;

            fn fun() {
                ().foo();
            }
        }

        mod baz {
            use crate::foo::Trait1;

            fn fun() {
                ().foo();
            }
        }
    """)

    fun `test ambiguous trait import 2`() = doCopyPasteTest("""
        //- lib.rs
        mod foo {
            pub trait Trait1 {
                fn foo(&self) {}
            }
            impl Trait1 for () {
                fn foo(&self) {}
            }

            pub trait Trait2 {
                fn foo(&self) {}
            }
            impl Trait2 for () {
                fn foo(&self) {}
            }
        }

        mod bar {
            use crate::foo::Trait2;

            <selection>fn fun() {
                ().foo();
            }</selection>
        }

        mod baz {
            /*caret*/
        }
    """, """
        //- lib.rs
        mod foo {
            pub trait Trait1 {
                fn foo(&self) {}
            }
            impl Trait1 for () {
                fn foo(&self) {}
            }

            pub trait Trait2 {
                fn foo(&self) {}
            }
            impl Trait2 for () {
                fn foo(&self) {}
            }
        }

        mod bar {
            use crate::foo::Trait2;

            fn fun() {
                ().foo();
            }
        }

        mod baz {
            use crate::foo::Trait2;

            fn fun() {
                ().foo();
            }
        }
    """)

    fun `test do not import private item`() = doCopyPasteTest("""
        //- lib.rs
        mod a {
            pub struct S;
            pub fn foo1() -> S { S }
        }

        mod b {
            struct S;
            <selection>fn foo2() -> S { S }</selection>
        }

        /*caret*/
    """, """
        //- lib.rs
        mod a {
            pub struct S;
            pub fn foo1() -> S { S }
        }

        mod b {
            struct S;
            fn foo2() -> S { S }
        }

        fn foo2() -> S { S }
    """)

    fun `test copy paste same location`() = doCopyPasteTest("""
        //- lib.rs
        mod foo {
            pub struct S;
        }

        mod bar {
            use crate::foo::S;

            <selection>fn fun(_: S) {}</selection>
            fn fun2() {}
            /*caret*/
        }
    """, """
        //- lib.rs
        mod foo {
            pub struct S;
        }

        mod bar {
            use crate::foo::S;

            fn fun(_: S) {}
            fn fun2() {}
            fn fun(_: S) {}
        }
    """)

    fun `test pat qualified path`() = doCopyPasteTest("""
        //- lib.rs
        mod foo {
            pub mod consts {
                pub const CONST: u32 = 1;
            }

            fn bar(a: u32) -> u32 {
                <selection>match a {
                    consts::CONST => 1,
                    _ => 2
                }</selection>
            }
        }

        mod baz {
            fn bar(a: u32) -> u32 {
                /*caret*/
            }
        }
    """, """
        //- lib.rs
        mod foo {
            pub mod consts {
                pub const CONST: u32 = 1;
            }

            fn bar(a: u32) -> u32 {
                match a {
                    consts::CONST => 1,
                    _ => 2
                }
            }
        }

        mod baz {
            use crate::foo::consts;

            fn bar(a: u32) -> u32 {
                match a {
                    consts::CONST => 1,
                    _ => 2
                }
            }
        }
    """)

    fun `test pat binding`() = doCopyPasteTest("""
        //- lib.rs
        mod foo {
            pub const CONST: u32 = 1;

            fn bar(a: u32) -> u32 {
                <selection>match a {
                    CONST => 1,
                    _ => 2
                }</selection>
            }
        }

        mod baz {
            fn bar(a: u32) -> u32 {
                /*caret*/
            }
        }
    """, """
        //- lib.rs
        mod foo {
            pub const CONST: u32 = 1;

            fn bar(a: u32) -> u32 {
                match a {
                    CONST => 1,
                    _ => 2
                }
            }
        }

        mod baz {
            use crate::foo::CONST;

            fn bar(a: u32) -> u32 {
                match a {
                    CONST => 1,
                    _ => 2
                }
            }
        }
    """)

    fun `test copy with whitespace`() = doCopyPasteTest("""
        //- lib.rs
        mod foo {
            pub struct S;
        }

        mod bar {
            use crate::foo::S;<selection>

            fn fun(_: S) {}</selection>
        }

        mod baz {
            /*caret*/
        }
    """, """
        //- lib.rs
        mod foo {
            pub struct S;
        }

        mod bar {
            use crate::foo::S;

            fn fun(_: S) {}
        }

        mod baz {
            use crate::foo::S;

            fn fun(_: S) {}
        }
    """)

    private fun doCopyPasteTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) = withImportOnPaste {
        val testProject = fileTreeFromText(before).create()
        myFixture.configureFromTempProjectFile(testProject.fileWithSelection)
        myFixture.performEditorAction(IdeActions.ACTION_COPY)

        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
        myFixture.performEditorAction(IdeActions.ACTION_PASTE)

        fileTreeFromText(after).assertEquals(myFixture.findFileInTempDir("."))
    }

    private fun withImportOnPaste(action: () -> Unit) {
        val settings = RsCodeInsightSettings.getInstance()
        val oldValue = settings.importOnPaste
        settings.importOnPaste = true
        try {
            action()
        } finally {
            settings.importOnPaste = oldValue
        }
    }
}
