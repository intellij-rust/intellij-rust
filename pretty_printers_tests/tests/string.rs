// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print s1
// lldbg-check:[...]$0 = "A∆й中" [...]
// lldbg-check-201:[...]s1 = "A∆й中" [...]
// lldb-command:print s2
// lldbg-check:[...]$1 = "A∆й中" [...]
// lldbg-check-201:[...]s2 = "A∆й中" [...]
// lldb-command:print empty_s1
// lldbg-check:[...]$2 = "" [...]
// lldbg-check-201:[...]empty_s1 = "" [...]
// lldb-command:print empty_s2
// lldbg-check:[...]$3 = "" [...]
// lldbg-check-201:[...]empty_s2 = "" [...]

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
