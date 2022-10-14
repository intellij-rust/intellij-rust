// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print range
// lldbr-check:[...]range = 1..12 [...]
// lldbg-check:[...]$0 = 1..12 [...]

// lldb-command:print range_from
// lldbr-check:[...]range_from = 9.. [...]
// lldbg-check:[...]$1 = 9.. [...]

// lldb-command:print range_inclusive
// lldbr-check:[...]range_inclusive = 32..=80 [...]
// lldbg-check:[...]$2 = 32..=80 [...]

// lldb-command:print range_to
// lldbr-check:[...]range_to = ..42 [...]
// lldbg-check:[...]$3 = ..42 [...]

// lldb-command:print range_to_inclusive
// lldbr-check:[...]range_to_inclusive = ..=120 [...]
// lldbg-check:[...]$4 = ..=120 [...]

// === GDB TESTS ===================================================================================

// gdb-command:run

// gdb-command:print range
// gdb-check:[...]$1 = 1..12
// gdb-command:print range_from
// gdb-check:[...]$2 = 9..
// gdb-command:print range_inclusive
// gdb-check:[...]$3 = 32..=80
// gdb-command:print range_to
// gdb-check:[...]$4 = ..42
// gdb-command:print range_to_inclusive
// gdb-check:[...]$5 = ..=120


fn main() {
    let range = 1..12;
    let range_from = 9..;
    let range_inclusive = 32..=80;
    let range_to = ..42;
    let range_to_inclusive = ..=120;
    print!(""); // #break
}
