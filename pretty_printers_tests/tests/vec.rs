// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print v
// lldbr-check:[...]v = size=2 { [0] = size=2 { [0] = 1 [1] = 2 } [1] = size=1 { [0] = 3 } }
// lldbg-check:[...]$0 = size=2 { [0] = size=2 { [0] = 1 [1] = 2 } [1] = size=1 { [0] = 3 } }
// lldb-command:print empty_v
// lldbr-check:[...]empty_v = size=0
// lldbg-check:[...]$1 = size=0

// === GDB TESTS ===================================================================================

// gdb-command:run

// gdb-command:print v
// gdb-check:[...]$1 = size=2 = {size=2 = {1, 2}, size=1 = {3}}
// gdb-command:print empty_v
// gdb-check:[...]$2 = size=0

fn main() {
    let v = vec![vec![1, 2], vec![3]];
    let empty_v: Vec<i32> = vec![];
    print!(""); // #break
}
