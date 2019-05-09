/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor

@ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
class RsStdlibErrorAnnotatorTest : RsAnnotatorTestBase(RsErrorAnnotator::class.java) {
    fun `test E0428 respects crate aliases`() = checkErrors("""
        extern crate core as core_alias;
        mod core {}

        // FIXME: ideally we want to highlight these
        extern crate alloc;
        mod alloc {}
    """)

    fun `test E0463 unknown crate`() = checkErrors("""
        extern crate alloc;

        <error descr="Can't find crate for `litarvan` [E0463]">extern crate litarvan;</error>
    """)

    fun `test impl u8 E0118`() = checkErrors("""
        impl <error descr="Can impl only `struct`s, `enum`s, `union`s and trait objects [E0118]">u8</error> {}
    """)

    fun `test impl feature lang u8 E0118`() = checkErrors("""
        #![feature(lang_items)]
        #[lang = "u8"]
        impl u8 {}
    """)

    fun `test no core impl u8 E0118`() = checkErrors("""
        #[no_core]
        impl <error descr="Can impl only `struct`s, `enum`s, `union`s and trait objects [E0118]">u8</error> {}
    """)

    fun `test feature no core impl u8 E0118`() = checkErrors("""
        #![feature(no_core)]
        #[no_core]
        impl <error descr="Can impl only `struct`s, `enum`s, `union`s and trait objects [E0118]">u8</error> {}
    """)

}
