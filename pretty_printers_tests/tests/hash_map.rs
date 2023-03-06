// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print xs
// lldbr-check:[...]xs = size=4 { [0] = (0 = 1, 1 = 10) [1] = (0 = 2, 1 = 20) [2] = (0 = 3, 1 = 30) [3] = (0 = 4, 1 = 40) }
// lldbg-check:[...]$0 = size=4 { [0] = (__0 = 1, __1 = 10) [1] = (__0 = 2, __1 = 20) [2] = (__0 = 3, __1 = 30) [3] = (__0 = 4, __1 = 40) }
// lldb-command:print ys
// lldbr-check:[...]ys = size=4 { [0] = 1 [1] = 2 [2] = 3 [3] = 4 }
// lldbg-check:[...]$1 = size=4 { [0] = 1 [1] = 2 [2] = 3 [3] = 4 }

// === GDB TESTS ===================================================================================

// gdb-command:run

// gdb-command:print xs
// gdb-check:[...]$1 = size=4 = {[1] = 10, [2] = 20, [3] = 30, [4] = 40}
// gdb-command:print ys
// gdb-check:[...]$2 = size=4 = {1, 2, 3, 4}


use std::collections::{HashMap, HashSet};
use std::hash::{BuildHasherDefault, Hasher};

#[derive(Default)]
struct SimpleHasher { hash: u64 }

impl Hasher for SimpleHasher {
    fn finish(&self) -> u64 { self.hash }
    fn write(&mut self, bytes: &[u8]) {}
    fn write_u64(&mut self, i: u64) { self.hash = i }
}

fn main() {
    let mut xs = HashMap::<u64, u64, BuildHasherDefault<SimpleHasher>>::default();
    for x in 1..5 {
        xs.insert(x, x * 10);
    }
    let mut ys = HashSet::<u64, BuildHasherDefault<SimpleHasher>>::default();
    for y in 1..5 {
        ys.insert(y);
    }
    print!(""); // #break
}
