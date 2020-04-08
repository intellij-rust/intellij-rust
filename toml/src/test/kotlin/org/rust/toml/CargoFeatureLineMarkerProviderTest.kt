/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor

@ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
class CargoFeatureLineMarkerProviderTest : CargoTomlLineMarkerProviderTestBase() {
    fun `test simple features`() = doTestByText("""
        [features]
        foo = []  # - Toggle feature `foo`
        bar = []  # - Toggle feature `bar`
        foobar = ["foo", "bar"]  # - Toggle feature `foobar`
    """)
}
