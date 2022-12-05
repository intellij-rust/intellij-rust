// max-version: 1.66.1

// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:frame variable d
// lldb-check:(alloc::collections::vec_deque::VecDeque<i32,[...]>) d = size=7 { [0] = 2 [1] = 3 [2] = 4 [3] = 5 [4] = 6 [5] = 7 [6] = 8 }

// lldb-command:frame variable empty_d
// lldb-check:(alloc::collections::vec_deque::VecDeque<i32,[...]>) empty_d = size=0

// === GDB TESTS ==================================================================================

// gdb-command:run

// gdb-command:print d
// gdb-check:[...]$1 = size=7 = {2, 3, 4, 5, 6, 7, 8}
// gdb-command:print empty_d
// gdb-check:[...]$2 = size=0

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
