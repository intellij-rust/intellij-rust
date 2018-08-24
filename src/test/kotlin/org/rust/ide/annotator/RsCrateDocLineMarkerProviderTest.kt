/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor
import org.rust.ide.lineMarkers.RsLineMarkerProviderTestBase

@ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
class RsCrateDocLineMarkerProviderTest : RsLineMarkerProviderTestBase() {
    fun `test documentation link`() = doTestByText("""
        #[cfg(not(windows))]
        extern crate dep_lib;             // - Open documentation for `dep_lib`
    """)

    fun `test documentation link with alias`() = doTestByText("""
        extern crate dep_lib as dlib;     // - Open documentation for `dep_lib`
    """)

    fun `test no documentation link`() = doTestByText("""
        extern crate nosrc_lib;           // It's a package name, no documentation
    """)
}
