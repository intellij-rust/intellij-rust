// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print r
// lldbg-check:[...]$0 = strong=2, weak=1 { value = 42 }
// lldb-command:print a
// lldbg-check:[...]$1 = strong=2, weak=1 { data = 42 }

use std::rc::Rc;
use std::sync::Arc;

fn main() {
    let r = Rc::new(42);
    let r1 = Rc::clone(&r);
    let w1 = Rc::downgrade(&r);

    let a = Arc::new(42);
    let a1 = Arc::clone(&a);
    let w2 = Arc::downgrade(&a);

    print!(""); // #break
}
