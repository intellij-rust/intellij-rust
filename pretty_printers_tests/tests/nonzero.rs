// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print a
// lldbr-check:[...]a = -1 [...]
// lldbg-check:[...]$0 = -1 [...]
// lldb-command:print b
// lldbr-check:[...]b = 1024 [...]
// lldbg-check:[...]$1 = 1024 [...]

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
