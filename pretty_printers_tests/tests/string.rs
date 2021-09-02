// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print s1
// lldbr-check:[...]s1 = "A∆й中" [...]
// lldbg-check:[...]$0 = "A∆й中" [...]
// lldb-command:print s2
// lldbr-check:[...]s2 = "A∆й中" [...]
// lldbg-check:[...]$1 = "A∆й中" [...]
// lldb-command:print s3
// lldbr-check:[...]s3 = "A∆й中" [...]
// lldbg-check:[...]$2 = "A∆й中" [...]
// lldb-command:print s4
// lldbr-check:[...]s4 = "A∆й中" [...]
// lldbg-check:[...]$3 = "A∆й中" [...]
// lldb-command:print s5
// lldbr-check:[...]s5 = "A∆й中" [...]
// lldbg-check:[...]$4 = "A∆й中" [...]
// lldb-command:print empty_s1
// lldbr-check:[...]empty_s1 = "" [...]
// lldbg-check:[...]$5 = "" [...]
// lldb-command:print empty_s2
// lldbr-check:[...]empty_s2 = "" [...]
// lldbg-check:[...]$6 = "" [...]
// lldb-command:print empty_s3
// lldbr-check:[...]empty_s3 = "" [...]
// lldbg-check:[...]$7 = "" [...]
// lldb-command:print empty_s4
// lldbr-check:[...]empty_s4 = "" [...]
// lldbg-check:[...]$8 = "" [...]
// lldb-command:print empty_s5
// lldbr-check:[...]empty_s5 = "" [...]
// lldbg-check:[...]$9 = "" [...]

// === GDB TESTS ==================================================================================

// gdb-command:run

// gdb-command:print s1
// gdb-check:[...]$1 = "A∆й中"
// gdb-command:print s2
// gdb-check:[...]$2 = "A∆й中"
// gdb-command:print empty_s1
// gdb-check:[...]$3 = ""
// gdb-command:print empty_s2
// gdb-check:[...]$4 = ""


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
