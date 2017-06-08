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

    fun `test vec!`() = testExpr("""
        fn main() {
            let x = vec!(1, 2u16, 4, 8);
            x
          //^ Vec<u16>
        }
    """)

    fun `test vec! no_std`() = testExpr("""
        #![no_std]
        fn main() {
            let x = vec!(1, 2u16, 4, 8);
            x
          //^ Vec<u16>
        }
    """)

    fun `test format!`() = testExpr("""
        fn main() {
            let x = format!("{} {}", "Hello", "world!");
            x
          //^ String
        }
    """)

    fun `test format! no_std`() = testExpr("""
        #![no_std]
        fn main() {
            let x = format!("{} {}", "Hello", "world!");
            x
          //^ String
        }
    """)

    fun `test format_args!`() = testExpr("""
        fn main() {
            let x = format_args!("{} {}", "Hello", "world!");
            x
          //^ Arguments
        }
    """)

    fun `test format_args! no_std`() = testExpr("""
        #![no_std]
        fn main() {
            let x = format_args!("{} {}", "Hello", "world!");
            x
          //^ Arguments
        }
    """)

    fun `test assert!`() = testExpr("""
        fn main() {
            let x = assert!(1 != 2);
            x
          //^ ()
        }
    """)

    fun `test debug_assert!`() = testExpr("""
        fn main() {
            let x = debug_assert!(1 != 2);
            x
          //^ ()
        }
    """)

    fun `test assert_eq!`() = testExpr("""
        fn main() {
            let x = assert_eq!(1 + 1, 2);
            x
          //^ ()
        }
    """)

    fun `test assert_ne!`() = testExpr("""
        fn main() {
            let x = assert_ne!(1, 2);
            x
          //^ ()
        }
    """)

    fun `test debug_assert_eq!`() = testExpr("""
        fn main() {
            let x = debug_assert_eq!(1 + 1, 2);
            x
          //^ ()
        }
    """)

    fun `test debug_assert_ne!`() = testExpr("""
        fn main() {
            let x = debug_assert_ne!(1, 2);
            x
          //^ ()
        }
    """)

    fun `test panic!`() = testExpr("""
        fn main() {
            let x = panic!("Something went wrong");
            x
          //^ ()
        }
    """)

    fun `test print!`() = testExpr("""
        fn main() {
            let x = print!("Something went wrong");
            x
          //^ ()
        }
    """)

    fun `test println!`() = testExpr("""
        fn main() {
            let x = println!("Something went wrong");
            x
          //^ ()
        }
    """)

    //From the log crate
    fun `test warn!`() = testExpr("""
        fn main() {
            let x = warn!("Something went wrong");
            x
          //^ ()
        }
    """)
}
