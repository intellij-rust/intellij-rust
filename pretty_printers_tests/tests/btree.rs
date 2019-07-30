// min-version: 1.33.0

// === GDB TESTS ===================================================================================

// gdb-command:run

// gdb-command: print btree_set
// gdbg-check:$1 = size=15 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14}

// gdb-command: print btree_map
// gdbg-check:$2 = size=15 = {[0] = 0, [1] = 1, [2] = 2, [3] = 3, [4] = 4, [5] = 5, [6] = 6, [7] = 7, [8] = 8, [9] = 9, [10] = 10, [11] = 11, [12] = 12, [13] = 13, [14] = 14}


use std::collections::{BTreeMap, BTreeSet};

fn main() {
    let mut btree_set = BTreeSet::new();
    for i in 0..15 {
        btree_set.insert(i);
    }

    let mut btree_map = BTreeMap::new();
    for i in 0..15 {
        btree_map.insert(i, i);
    }
    print!(""); // #break
}
