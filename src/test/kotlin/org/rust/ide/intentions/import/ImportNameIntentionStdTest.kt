/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.import

import com.intellij.testFramework.LightProjectDescriptor
import org.rust.ide.intentions.RsIntentionTestBase

class ImportNameIntentionStdTest : RsIntentionTestBase(ImportNameIntention()) {

    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibAndDependencyRustProjectDescriptor

    fun `test import struct from std crate`() = doAvailableTest("""
        fn foo<T: Read/*caret*/>(t: T) {}
    """, """
        use std::io::Read;

        fn foo<T: Read/*caret*/>(t: T) {}
    """, ImportNameIntention.Testmarks.autoInjectedCrate)

    fun `test import struct from not std crate`() = doAvailableTestWithFileTree("""
        //- dep-lib/lib.rs
        pub mod foo {
            pub struct Bar;
        }
        //- main.rs
        fn foo(t: Bar/*caret*/) {}
    """, """
        extern crate dep_lib_target;

        use dep_lib_target::foo::Bar;

        fn foo(t: Bar/*caret*/) {}
    """)

    fun `test don't insert extern crate item it is already exists`() = doAvailableTestWithFileTree("""
        //- dep-lib/lib.rs
        pub mod foo {
            pub struct Bar;
        }
        //- main.rs
        extern crate dep_lib_target;

        fn foo(t: Bar/*caret*/) {}
    """, """
        extern crate dep_lib_target;

        use dep_lib_target::foo::Bar;

        fn foo(t: Bar/*caret*/) {}
    """)

    fun `test insert new extern crate item after existing extern crate items`() = doAvailableTestWithFileTree("""
        //- dep-lib/lib.rs
        pub mod foo {
            pub struct Bar;
        }
        //- main.rs
        extern crate some_crate;

        fn foo(t: Bar/*caret*/) {}
    """, """
        extern crate some_crate;
        extern crate dep_lib_target;

        use dep_lib_target::foo::Bar;

        fn foo(t: Bar/*caret*/) {}
    """)
}
