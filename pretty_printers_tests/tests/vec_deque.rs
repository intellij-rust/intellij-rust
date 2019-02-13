// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print d
// lldbg-check:[...]$0 = size=7 { [0] = 2 [1] = 3 [2] = 4 [3] = 5 [4] = 6 [5] = 7 [6] = 8 }
// lldb-command:print empty_d
// lldbg-check:[...]$1 = size=0

// === LLDB TESTS ==================================================================================

// gdb-command:run

// gdb-command:print d
// gdbg-check:[...]$1 = size=7 = {2, 3, 4, 5, 6, 7, 8}
// gdb-command:print empty_d
// gdbg-check:[...]$2 = size=0

use std::collections::VecDeque;

fn main() {
    let mut d = VecDeque::new();
    for i in 1..8 {
        d.push_back(i)
    }
    d.pop_front();
    d.push_back(8);

    let empty_d: VecDeque<i32> = VecDeque::new();
    print!(""); // #break
}
