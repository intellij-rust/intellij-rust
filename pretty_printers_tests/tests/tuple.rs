// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print t1
// lldbr-check:[...]t1 = { 0 = (0 = 1, 1 = 2) 1 = 3 }
// lldbg-check:[...]$0 = { __0 = (__0 = 1, __1 = 2) __1 = 3 }

// lldb-command:print t2
// lldbr-check:[...]t2 = { 0 = "abc" [...] 1 = 42 }
// lldbg-check:[...]$1 = { __0 = "abc" [...] __1 = 42 }

// === GDB TESTS ==================================================================================

// gdb-command:run

// gdb-command:print t1
// gdb-check:[...]$1 = size=2 = {size=2 = {1, 2}, 3}
// gdb-command:print t2
// gdb-check:[...]$2 = size=2 = {"abc", 42}

fn main() {
    let t1 = ((1, 2), 3);
    let t2 = ("abc", 42);
    print!(""); // #break
}
