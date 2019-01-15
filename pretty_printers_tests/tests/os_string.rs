// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print s
// lldbg-check:[...]$0 = "A∆й中" [...]
// lldb-command:print empty
// lldbg-check:[...]$1 = "" [...]


use std::ffi::OsString;

fn main() {
    let s = OsString::from("A∆й中");
    let empty = OsString::from("");
    print!(""); // #break
}
