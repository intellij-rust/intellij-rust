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

        // FIXME: ideally we want to highlight these
        extern crate alloc;
        mod alloc {}
    """)
}
