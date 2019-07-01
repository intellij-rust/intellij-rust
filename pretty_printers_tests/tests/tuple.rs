// min-version: 1.30.0

// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print t1
// lldbg-check:[...]$0 = { 0 = { 0 = 1 1 = 2 } 1 = 3 }
// lldb-command:print t2
// lldbg-check:[...]$1 = { 0 = "abc" [...] 1 = 42 }

// === GDB TESTS ==================================================================================

// gdb-command:run

// gdb-command:print t1
// gdbg-check:[...]$1 = size=2 = {size=2 = {1, 2}, 3}
// gdb-command:print t2
// gdbg-check:[...]$2 = size=2 = {"abc", 42}

fn main() {
    let t1 = ((1, 2), 3);
    let t2 = ("abc", 42);
    print!(""); // #break
}
