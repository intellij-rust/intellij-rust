package org.rust.lang.core.resolve

import org.rust.cargo.CargoProjectDescription

class RustMultiFileResolveTestCaseLibraryTarget : RustMultiFileResolveTestCaseBase() {
    override val targets: Collection<CargoProjectDescription.Target> = listOf(libTarget("lib.rs"))

    fun testGlobalPath()    = doTestResolved("global_path/foo.rs", "global_path/lib.rs", "global_path/bar.rs")
    fun testUseViewPath()   = doTestResolved("global_path/foo.rs", "global_path/lib.rs", "global_path/bar.rs")
}
