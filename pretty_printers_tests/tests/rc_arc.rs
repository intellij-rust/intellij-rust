// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print r
// lldbr-check:[...]r = strong=2, weak=1 { value = 42 }
// lldbg-check:[...]$0 = strong=2, weak=1 { value = 42 }
// lldb-command:print r_weak
// lldbr-check:[...]r_weak = strong=2, weak=1 { value = 42 }
// lldbg-check:[...]$1 = strong=2, weak=1 { value = 42 }

// lldb-command:print a
// lldbr-check:[...]a = strong=2, weak=1 { data = 42 }
// lldbg-check:[...]$2 = strong=2, weak=1 { data = 42 }
// lldb-command:print a_weak
// lldbr-check:[...]a_weak = strong=2, weak=1 { data = 42 }
// lldbg-check:[...]$3 = strong=2, weak=1 { data = 42 }

// === GDB TESTS ==================================================================================

// gdb-command:run

// gdb-command:print r
// gdb-check:[...]$1 = strong=2, weak=1 = {value = 42, strong = 2, weak = 1}
// gdb-command:print r_weak
// gdb-check:[...]$2 = strong=2, weak=1 = {value = 42, strong = 2, weak = 1}
// gdb-command:print a
// gdb-check:[...]$3 = strong=2, weak=1 = {value = 42, strong = 2, weak = 1}
// gdb-command:print a_weak
// gdb-check:[...]$4 = strong=2, weak=1 = {value = 42, strong = 2, weak = 1}

use std::rc::Rc;
use std::sync::Arc;

fn main() {
    let r = Rc::new(42);
    let r1 = Rc::clone(&r);
    let r_weak = Rc::downgrade(&r);

    let a = Arc::new(42);
    let a1 = Arc::clone(&a);
    let a_weak = Arc::downgrade(&a);

    print!(""); // #break
}
