// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print s1
// lldbg-check:[...]s1 = "A∆й中" [...]
// lldb-command:print s2
// lldbg-check:[...]s2 = "A∆й中" [...]
// lldb-command:print empty_s1
// lldbg-check:[...]empty_s1 = "" [...]
// lldb-command:print empty_s2
// lldbg-check:[...]empty_s2 = "" [...]

// === GDB TESTS ==================================================================================

// gdb-command:run

// gdb-command:print s1
// gdbg-check:[...]$1 = "A∆й中"
// gdb-command:print s2
// gdbg-check:[...]$2 = "A∆й中"
// gdb-command:print empty_s1
// gdbg-check:[...]$3 = ""
// gdb-command:print empty_s2
// gdbg-check:[...]$4 = ""


fn main() {
    let s1 = "A∆й中";
    let s2 = String::from(s1);
    let empty_s1 = "";
    let empty_s2 = String::from(empty_s1);
    print!(""); // #break
}
