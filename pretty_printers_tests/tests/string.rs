// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print s1
// lldbg-check:[...]s1 = "A∆й中" [...]
// lldb-command:print s2
// lldbg-check:[...]s2 = "A∆й中" [...]
// lldb-command:print s3
// lldbg-check:[...]s3 = "A∆й中" [...]
// lldb-command:print s4
// lldbg-check:[...]s4 = "A∆й中" [...]
// lldb-command:print s5
// lldbg-check:[...]s5 = "A∆й中" [...]
// lldb-command:print empty_s1
// lldbg-check:[...]empty_s1 = "" [...]
// lldb-command:print empty_s2
// lldbg-check:[...]empty_s2 = "" [...]
// lldb-command:print empty_s3
// lldbg-check:[...]empty_s3 = "" [...]
// lldb-command:print empty_s4
// lldbg-check:[...]empty_s4 = "" [...]
// lldb-command:print empty_s5
// lldbg-check:[...]empty_s5 = "" [...]

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
    let mut s1 = "A∆й中";
    let mut s2 = String::from(s1);
    let s3 = s2.as_mut_str();
    let s4 = s3 as *mut str;
    let s5 = s1 as *const str;

    let mut empty_s1 = "";
    let mut empty_s2 = String::from(empty_s1);
    let empty_s3 = empty_s2.as_mut_str();
    let empty_s4 = empty_s3 as *mut str;
    let empty_s5 = empty_s1 as *const str;

    print!(""); // #break
}
