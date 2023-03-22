// === LLDB TESTS ==================================================================================

// lldb-command:run

// lldb-command:frame variable --ptr-depth 1 vec_ref
// lldb-check:[...]vec_ref = 0x[...] size=3 { [0] = 1 [1] = 2 [2] = 3 }

// lldb-command:frame variable --ptr-depth 1  vec_const_ptr
// lldb-check:[...]vec_const_ptr = 0x[...] size=3 { [0] = 1 [1] = 2 [2] = 3 }

// lldb-command:frame variable --ptr-depth 1  vec_mut_ref
// lldb-check:[...]vec_mut_ref = 0x[...] size=3 { [0] = 1 [1] = 2 [2] = 3 }

// lldb-command:frame variable --ptr-depth 1  vec_mut_ptr
// lldb-check:[...]vec_mut_ptr = 0x[...] size=3 { [0] = 1 [1] = 2 [2] = 3 }

// lldb-command:frame variable --ptr-depth 1  box_vec
// lldb-check:[...]box_vec = 0x[...] size=3 { [0] = 1 [1] = 2 [2] = 3 }


fn main() {
    let vec = vec![1, 2, 3];
    let vec_ref = &vec;
    let vec_const_ptr = &vec as *const Vec<i32>;

    let mut vec_mut = vec![1, 2, 3];
    let vec_mut_ref = &mut vec_mut;
    let vec_mut_ptr = &mut vec_mut as *mut Vec<i32>;

    let box_vec = Box::new(vec);

    print!(""); // #break
}
