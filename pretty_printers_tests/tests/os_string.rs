// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print s
// lldbg-check:[...]$0 = "A∆й中" [...]
// lldb-command:print empty
// lldbg-check:[...]$1 = "" [...]

// === GDB TESTS ==================================================================================

// gdb-command:run

// gdb-command:print s
// gdbg-check:[...]$1 = "A∆й中"
// gdb-command:print empty
// gdbg-check:[...]$2 = ""


use std::ffi::OsString;

fn main() {
    let s = OsString::from("A∆й中");
    let empty = OsString::from("");
    print!(""); // #break
}
