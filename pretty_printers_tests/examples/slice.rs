// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:frame variable slice
// lldb-unix-check:(&[i32]) slice = size=2 { [0] = 1 [1] = 2 }
// lldb-windows-check:[...] slice = size=2 { [0] = 1 [1] = 2 }

// lldb-command:frame variable slice_mut
// lldb-unix-check:(&mut [i32]) slice_mut = size=2 { [0] = 1 [1] = 2 }
// lldb-windows-check:[...] slice_mut = size=2 { [0] = 1 [1] = 2 }

// lldb-command:frame variable slice_ptr_const
// lldb-unix-check:(*const [i32]) slice_ptr_const = size=2 { [0] = 1 [1] = 2 }
// lldb-windows-check:[...] slice_ptr_const = size=2 { [0] = 1 [1] = 2 }

// lldb-command:frame variable slice_ptr_mut
// lldb-unix-check:(*mut [i32]) slice_ptr_mut = size=2 { [0] = 1 [1] = 2 }
// lldb-windows-check:[...] slice_ptr_mut = size=2 { [0] = 1 [1] = 2 }

// lldb-command:frame variable --ptr-depth 1 slice_fixed_sized
// lldb-unix-check:(&[i32; 2]) slice_fixed_sized = 0x[...] { [0] = 1 [1] = 2 }
// lldb-windows-check:[...] slice_fixed_sized = 0x[...] { [0] = 1 [1] = 2 }

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
