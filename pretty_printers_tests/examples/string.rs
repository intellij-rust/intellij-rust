// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:frame variable s1
// lldb-unix-check:(&str) s1 = "A∆й中" [...]
// lldb-windows-check:[...] s1 = "A∆й中" [...]

// lldb-command:frame variable s2
// lldb-check:(alloc::string::String) s2 = "A∆й中" [...]

// lldb-command:frame variable s3
// lldb-unix-check:(&mut str) s3 = "A∆й中" [...]
// lldb-windows-check:[...] s3 = "A∆й中" [...]

// lldb-command:frame variable s4
// lldb-unix-check:(*mut str) s4 = "A∆й中" [...]
// lldb-windows-check:[...] s4 = "A∆й中" [...]

// lldb-command:frame variable s5
// lldb-unix-check:(*const str) s5 = "A∆й中" [...]
// lldb-windows-check:[...] s5 = "A∆й中" [...]

// lldb-command:frame variable empty_s1
// lldb-unix-check:(&str) empty_s1 = "" [...]
// lldb-windows-check:[...] empty_s1 = "" [...]

// lldb-command:frame variable empty_s2
// lldb-check:(alloc::string::String) empty_s2 = "" [...]

// lldb-command:frame variable empty_s3
// lldb-unix-check:(&mut str) empty_s3 = "" [...]
// lldb-windows-check:[...] empty_s3 = "" [...]

// lldb-command:frame variable empty_s4
// lldb-unix-check:(*mut str) empty_s4 = "" [...]
// lldb-windows-check:[...] empty_s4 = "" [...]

// lldb-command:frame variable empty_s5
// lldb-unix-check:(*const str) empty_s5 = "" [...]
// lldb-windows-check:[...] empty_s5 = "" [...]

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

    // if empty_s5 is not used before the breakpoint,
    // debugger will throw "error: could not find item" when trying to frame it
    println!("{:?}", empty_s5);

    print!(""); // #break
}
