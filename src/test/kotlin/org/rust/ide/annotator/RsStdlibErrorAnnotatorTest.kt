/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor

@ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
class RsStdlibErrorAnnotatorTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test E0428 respects crate aliases`() = checkErrors("""
        extern crate core as core_alias;
        mod core {}

        <error descr="A type named `alloc` has already been defined in this module [E0260]">extern crate alloc;</error>
        mod <error descr="A type named `alloc` has already been defined in this module [E0260]">alloc</error> {}
    """)
}
