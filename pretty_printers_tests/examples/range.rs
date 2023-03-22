// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:frame variable range
// lldb-check:(core::ops::range::Range<i32>) range = 1..12 [...]

// lldb-command:frame variable range_from
// lldb-check:(core::ops::range::RangeFrom<i32>) range_from = 9.. [...]

// lldb-command:frame variable range_inclusive
// lldb-check:(core::ops::range::RangeInclusive<i32>) range_inclusive = 32..=80 [...]

// lldb-command:frame variable range_to
// lldb-check:(core::ops::range::RangeTo<i32>) range_to = ..42 [...]

// lldb-command:frame variable range_to_inclusive
// lldb-check:(core::ops::range::RangeToInclusive<i32>) range_to_inclusive = ..=120 [...]

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
