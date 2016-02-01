package org.rust.lang.core.resolve

class RustMultiFileResolveTestCaseLibraryTarget : RustMultiFileResolveTestCaseBase() {

    fun testGlobalPath()    = doTestResolved("global_path/foo.rs", "global_path/lib.rs", "global_path/bar.rs")
    fun testUseViewPath()   = doTestResolved("global_path/foo.rs", "global_path/lib.rs", "global_path/bar.rs")
}
