// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:frame variable a
// lldb-check:(core::num::nonzero::NonZeroI32) a = -1 [...]

// lldb-command:frame variable b
// lldb-check:(core::num::nonzero::NonZeroUsize) b = 1024 [...]

// === GDB TESTS ===================================================================================

// gdb-command:run

// gdb-command:print a
// gdb-check:[...]$1 = -1

// gdb-command:print b
// gdb-check:[...]$2 = 1024

use std::num::{NonZeroI32, NonZeroUsize};

fn main() {
    let a = NonZeroI32::new(-1).unwrap();
    let b = NonZeroUsize::new(1024).unwrap();
    print!(""); // #break
}
