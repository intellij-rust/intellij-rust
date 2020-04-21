// min-version: 1.34.0

// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print a
// lldbg-check:[...]$0 = -1 [...]
// lldbg-check-201:[...]a = -1 [...]
// lldb-command:print b
// lldbg-check:[...]$1 = 1024 [...]
// lldbg-check-201:[...]b = 1024 [...]

// === GDB TESTS ===================================================================================

// gdb-command:run

// gdb-command:print a
// gdbg-check:[...]$1 = -1

// gdb-command:print b
// gdbg-check:[...]$2 = 1024

use std::num::{NonZeroI32, NonZeroUsize};

fn main() {
    let a = NonZeroI32::new(-1).unwrap();
    let b = NonZeroUsize::new(1024).unwrap();
    print!(""); // #break
}
