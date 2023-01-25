// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:print slice
// lldbr-check:[...]slice = size=2 { [0] = 1 [1] = 2 }
// lldbg-check:[...]$0 = size=2 { [0] = 1 [1] = 2 }
// lldb-command:print slice_mut
// lldbr-check:[...]slice_mut = size=2 { [0] = 1 [1] = 2 }
// lldbg-check:[...]$1 = size=2 { [0] = 1 [1] = 2 }
// lldb-command:print slice_ptr_const
// lldbr-check:[...]slice_ptr_const = size=2 { [0] = 1 [1] = 2 }
// lldbg-check:[...]$2 = size=2 { [0] = 1 [1] = 2 }
// lldb-command:print slice_ptr_mut
// lldbr-check:[...]slice_ptr_mut = size=2 { [0] = 1 [1] = 2 }
// lldbg-check:[...]$3 = size=2 { [0] = 1 [1] = 2 }
// lldb-command:frame variable --ptr-depth 1 slice_fixed_sized
// lldb-check:[...]slice_fixed_sized = 0x[...] { [0] = 1 [1] = 2 }

// === GDB TESTS ===================================================================================

// gdb-command:run

// gdb-command:print slice
// gdb-check:[...]$1 = size=2 = {1, 2}
// gdb-command:print slice_mut
// gdb-check:[...]$2 = size=2 = {1, 2}
// gdb-command:print slice_ptr_const
// gdb-check:[...]$3 = size=2 = {1, 2}
// gdb-command:print slice_ptr_mut
// gdb-check:[...]$4 = size=2 = {1, 2}

fn main() {
    let slice: &[i32] = &[1, 2];
    let slice_mut: &mut [i32] = &mut [1, 2];
    let slice_ptr_const: *const [i32] = slice as *const [i32];
    let slice_ptr_mut: *mut [i32] = slice_mut as *mut [i32];
    let slice_fixed_sized: &[i32; 2] = &[1, 2];

    print!(""); // #break
}
