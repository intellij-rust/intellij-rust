// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:frame variable cell
// lldb-check:(core::cell::Cell<i32>) cell = (value = 42)

// lldb-command:frame variable ref_cell1
// lldb-check:(core::cell::RefCell<i32>) ref_cell1 = borrow=1 { value = 42 }

// lldb-command:frame variable ref1
// lldb-check:(core::cell::Ref<i32>) ref1 = borrow=1 { [...] = 42 }

// lldb-command:frame variable ref_mut2
// lldb-check:(core::cell::RefMut<i32>) ref_mut2 = borrow_mut=1 { [...] = 42 }

// === GDB TESTS ==================================================================================

// gdb-command:run

// gdb-command:print cell
// gdb-check:[...]$1 = {value = 42}
// gdb-command:print ref_cell1
// gdb-check:[...]$2 = borrow=1 = {value = 42, borrow = 1}
// gdb-command:print ref1
// gdb-check:[...]$3 = borrow=1 = {[...] = 42, borrow = 1}
// gdb-command:print ref_mut2
// gdb-check:[...]$4 = borrow_mut=1 = {[...] = 42, borrow = -1}


use std::cell::{Cell, RefCell};

fn main() {
    let cell = Cell::new(42);

    let ref_cell1 = RefCell::new(42);
    let ref1 = ref_cell1.borrow();

    let ref_cell2 = RefCell::new(42);
    let ref_mut2 = ref_cell2.borrow_mut();

    print!(""); // #break
}
