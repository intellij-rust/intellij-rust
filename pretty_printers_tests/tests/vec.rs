// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:frame variable v
// lldb-check:(alloc::vec::Vec<alloc::vec::Vec<i32,[...]>,[...]>) v = size=2 { [0] = size=2 { [0] = 1 [1] = 2 } [1] = size=1 { [0] = 3 } }

// lldb-command:frame variable empty_v
// lldb-check:(alloc::vec::Vec<i32,[...]>) empty_v = size=0

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
