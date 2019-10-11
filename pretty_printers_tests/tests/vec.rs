// max-version: 1.36.0

// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print v
// lldbg-check:[...]$0 = size=2 { [0] = size=2 { [0] = 1 [1] = 2 } [1] = size=1 { [0] = 3 } }
// lldb-command:print empty_v
// lldbg-check:[...]$1 = size=0

// === GDB TESTS ===================================================================================

// gdb-command:run

// gdb-command:print v
// gdbg-check:[...]$1 = size=2 = {size=2 = {1, 2}, size=1 = {3}}
// gdb-command:print empty_v
// gdbg-check:[...]$2 = size=0

fn main() {
    let v = vec![vec![1, 2], vec![3]];
    let empty_v: Vec<i32> = vec![];
    print!(""); // #break
}
