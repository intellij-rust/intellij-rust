// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print xs
// lldbg-check:[...]$0 = size=4 { [0] = { 0 = 1 1 = 10 } [1] = { 0 = 2 1 = 20 } [2] = { 0 = 3 1 = 30 } [3] = { 0 = 4 1 = 40 } }
// lldb-command:print ys
// lldbg-check:[...]$1 = size=4 { [0] = 10 [1] = 20 [2] = 30 [3] = 40 }

// === GDB TESTS ===================================================================================

// gdb-command:run

// gdb-command:print xs
// gdbg-check:[...]$1 = size=4 = {[1] = 10, [2] = 20, [3] = 30, [4] = 40}
// gdb-command:print ys
// gdbg-check:[...]$2 = size=4 = {10, 20, 30, 40}


use std::collections::{HashMap, HashSet};
use std::hash::{BuildHasherDefault, Hasher};

#[derive(Default)]
struct ConstHasher;

impl Hasher for ConstHasher {
    fn finish(&self) -> u64 { 42 }
    fn write(&mut self, bytes: &[u8]) {}
}

fn main() {
    let mut xs = HashMap::<u32, u32, BuildHasherDefault<ConstHasher>>::default();
    for x in 1..5 {
        xs.insert(x, x * 10);
    }
    let mut ys = HashSet::<u32, BuildHasherDefault<ConstHasher>>::default();
    for y in 1..5 {
        ys.insert(y * 10);
    }
    print!(""); // #break
}
