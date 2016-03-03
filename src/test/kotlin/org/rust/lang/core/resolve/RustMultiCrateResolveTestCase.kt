package org.rust.lang.core.resolve

import org.rust.cargo.CargoProjectDescription
import org.rust.cargo.project.module.persistence.ExternCrateData

class RustMultiCrateResolveTestCase : RustMultiFileResolveTestCaseBase() {
    override val targets: Collection<CargoProjectDescription.Target> = listOf(binTarget("main.rs"), libTarget("lib.rs"))
    override val externCrates: Collection<ExternCrateData> = listOf(ExternCrateData(name = "my_lib", path = "lib.rs"))

    fun testLibraryAsCrate() = doTestResolved("library_as_crate/main.rs", "library_as_crate/lib.rs")
}
