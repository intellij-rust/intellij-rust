/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsRegionCheckInspectionTest : RsInspectionsTestBase(RsRegionCheckInspection()) {

    fun `test dangling reference`() = checkByText("""
        fn dangling_reference<'a>() -> &'a i32 {
            let n = 42;
            return <error>&n</error>
        }
    """)

//    fun `test reference outlives referent`() = checkByText("""
//        struct S<'a, T> {
//            <error>x: &'a T</error>
//        }
//    """)

    fun `test mismatched explicit lifetimes`() = checkByText("""
        fn push<'a, 'b>(v: &mut Vec<&'a i32>, x: &'b i32) {
            v.push(<error>x</error>)
        }
    """)

    fun `test mismatched elided lifetimes`() = checkByText("""
        fn push<'a, 'b>(v: &mut Vec<&'a i32>, x: &'b i32) {
            v.push(<error>x</error>)
        }
    """)

    fun `test matched lifetimes`() = checkByText("""
        fn push<'a, 'b: 'a>(v: &mut Vec<&'a i32>, x: &'b i32) {
            v.push(x)
        }
    """)

    fun `test element must outlive container`() = checkByText("""
        fn push<'a, 'b: 'a>(v: &mut Vec<&'a i32>, x: &'b i32) {
            v.push(x)
        }

        fn main() {
            let mut v = Vec::new();
            let x = 42;
            push(&mut v, <error>&x</error>)
        }
    """)

    fun `test element outlive container`() = checkByText("""
        fn push<'a, 'b: 'a>(v: &mut Vec<&'a i32>, x: &'b i32) {
            v.push(x)
        }

        fn main() {
            let x = 42;
            let mut v = Vec::new();
            push(&mut v, &x)
        }
    """)
}
