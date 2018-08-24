/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

/**
 * Tests for the CString Pointer inspection
 */
@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsCStringPointerInspectionTest : RsInspectionsTestBase(RsCStringPointerInspection()) {

    fun testInspection() = checkByText("""
        use std::ffi::{CString};

        fn main() {
            let s = CString::new("Hello").unwrap();
            let ptr1 = s.as_ptr();
            let ptr2 = <warning descr="Unsafe CString pointer">CString::new("World").unwrap().as_ptr()</warning>;
            unsafe {
                 *ptr1;
                 *ptr2;
            }
        }
    """)
}
