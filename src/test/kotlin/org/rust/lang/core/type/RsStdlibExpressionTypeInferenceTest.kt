package org.rust.lang.core.type

import org.rust.cargo.project.workspace.cargoWorkspace

class RsStdlibExpressionTypeInferenceTest : RsTypificationTestBase() {
    override fun getProjectDescriptor() = WithStdlibRustProjectDescriptor

    fun testHasStdlibSources() {
        val cargoProject = myModule.cargoWorkspace
        cargoProject?.findCrateByNameApproximately("std")?.crateRoot
            ?: error("No Rust sources found during test.\nTry running `rustup component add rust-src`")
    }

    fun `test RangeFull`() = testExpr("""
        fn main() {
            let x = ..;
            x
          //^ RangeFull
        }
    """)

    fun `test RangeFull no_std`() = testExpr("""
        #![no_std]
        fn main() {
            let x = ..;
            x
          //^ RangeFull
        }
    """)

    fun `test RangeFrom`() = testExpr("""
        fn main() {
            let x = 0u16..;
            x
          //^ RangeFrom<u16>
        }
    """)

    fun `test RangeTo`() = testExpr("""
        fn main() {
            let x = ..42u16;
            x
          //^ RangeTo<u16>
        }
    """)

    fun `test Range 2`() = testExpr("""
        fn main() {
            let x = 0u16..42;
            x
          //^ Range<u16>
        }
    """)

    fun `test Range 1`() = testExpr("""
        fn main() {
            let x = 0..42u16;
            x
          //^ Range<u16>
        }
    """)

    fun `test RangeToInclusive`() = testExpr("""
        fn main() {
            let x = ...42u16;
            x
          //^ RangeToInclusive<u16>
        }
    """)

    fun `test RangeInclusive 1`() = testExpr("""
        fn main() {
            let x = 0u16...42;
            x
          //^ RangeInclusive<u16>
        }
    """)

    fun `test RangeInclusive 2`() = testExpr("""
        fn main() {
            let x = 0...42u16;
            x
          //^ RangeInclusive<u16>
        }
    """)
}
