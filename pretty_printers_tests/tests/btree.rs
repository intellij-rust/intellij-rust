// === GDB TESTS ===================================================================================

// gdb-command:run

// gdb-command: print btree_set
// gdb-check:$1 = size=15 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14}

// gdb-command: print btree_map
// gdb-check:$2 = size=15 = {[0] = 0, [1] = 1, [2] = 2, [3] = 3, [4] = 4, [5] = 5, [6] = 6, [7] = 7, [8] = 8, [9] = 9, [10] = 10, [11] = 11, [12] = 12, [13] = 13, [14] = 14}

// gdb-command: print zst_btree_map
// gdb-check:$3 = size=1 = {[()] = ()}

// gdb-command: print zst_btree_set
// gdb-check:$4 = size=1 = {()}

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

    let mut zst_btree_map: BTreeMap<(), ()> = BTreeMap::new();
    zst_btree_map.insert((), ());

    let mut zst_btree_set: BTreeSet<()> = BTreeSet::new();
    zst_btree_set.insert(());

    print!(""); // #break
}
