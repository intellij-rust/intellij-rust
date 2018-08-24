/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor

@ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
class RsStdlibErrorAnnotatorTest : RsAnnotationTestBase() {
    fun `test E0428 respects crate aliases`() = checkErrors("""
        extern crate libc as libc_alias;
        mod libc {}

        // FIXME: ideally we want to highlight these
        extern crate alloc;
        mod alloc {}
    """)

    fun `test E0463 unknown crate`() = checkErrors("""
        extern crate alloc;

        <error descr="Can't find crate for `litarvan` [E0463]">extern crate litarvan;</error>
    """)
}
