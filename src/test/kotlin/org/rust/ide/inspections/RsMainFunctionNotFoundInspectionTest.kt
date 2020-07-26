/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor

class RsMainFunctionNotFoundInspectionTest : RsInspectionsTestBase(RsMainFunctionNotFoundInspection::class) {

    fun `test does not exist main function`() = checkByFileTree("""
    //- main.rs
        <error descr="`main` function not found in crate `test-package` [E0601]"> /*caret*/ </error>
    """)

    fun `test do not show if an error exists`() = checkByFileTree("""
    //- main.rs
        fn foo( <error>{</error>/*caret*/}
        fn main<error>(</error>) {}
    """)

    fun `test show if a nested error exists`() = checkByFileTree("""
    //- main.rs
        <error descr="`main` function not found in crate `test-package` [E0601]">
        fn foo() {
            fn bar( <error>{</error>/*caret*/}
        }<EOLError></EOLError>
        </error>
    """)

    fun `test has nested main function`() = checkByFileTree("""
    //- main.rs
        <error descr="`main` function not found in crate `test-package` [E0601]">fn foo() {
             fn main() {
                /*caret*/
             }
        }</error>
    """)

    fun `test exists no_main attr`() = checkByFileTree("""
    //- main.rs
        #![no_main]/*caret*/
    """)

    fun `test exists main function`() = checkByFileTree("""
    //- main.rs
       fn main() {
            /*caret*/
       }
    """)

    fun `test that the main function does not exist in the custom bin`() = checkByFileTree("""
    //- bin/a.rs
        <error descr="`main` function not found in crate `test-package` [E0601]"> /*caret*/ </error>
    """)

    fun `test for nested main function in custom bin`() = checkByFileTree("""
    //- bin/a.rs
        <error descr="`main` function not found in crate `test-package` [E0601]">fn foo() {
            fn main() {
                /*caret*/
            }
        }</error>
    """)

    fun `test that no_main attr exists in the custom bin`() = checkByFileTree("""
    //- bin/a.rs
        #![no_main]
        /*caret*/
    """)

    fun `test main function in custom binTest main function in custom bin`() = checkByFileTree("""
    //- bin/a.rs
        fn main() {
            /*caret*/
        }
    """)

    fun `test that the main function does not exist in the custom example bin`() = checkByFileTree("""
    //- example/a.rs
        <error descr="`main` function not found in crate `test-package` [E0601]"> /*caret*/ </error>
    """)

    fun `test for nested main function in custom example bin`() = checkByFileTree("""
    //- example/a.rs
        <error descr="`main` function not found in crate `test-package` [E0601]">fn foo() {
            fn main() {
                /*caret*/
            }
        }</error>
    """)

    fun `test that no_main attr exists in the custom example bin`() = checkByFileTree("""
    //- example/a.rs
        #![no_main]
        /*caret*/
    """)

    fun `test main function in custom example bin`() = checkByFileTree("""
    //- example/a.rs
        fn main() {
            /*caret*/
        }
    """)

    fun `test that the main function does not exist in the bench`() = checkByFileTree("""
    //- bench/a.rs
        fn foo() { /*caret*/ }
    """)

    fun `test that the main function does not exist in the example lib`() = checkByFileTree("""
    //- example-lib/a.rs
        fn foo() { /*caret*/ }
    """)

    fun `test main function does not exist in build file`() = checkByFileTree("""
    //- build.rs
        <error descr="`main` function not found in crate `build_script_build` [E0601]"> /*caret*/ </error>
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test that the main function does not exist in the external binary crate`() = checkByFileTree("""
    //- dep-lib/lib.rs
        fn foo() { /*caret*/ }
    """)
}
