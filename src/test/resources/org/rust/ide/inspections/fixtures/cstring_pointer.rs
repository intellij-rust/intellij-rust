use std::ffi::{CString};

fn main() {
    let s = CString::new("Hello").unwrap();
    let ptr1 = s.as_ptr();
    let ptr2 = <warning descr="Unsafe CString pointer">CString::new("World").unwrap().as_ptr()</warning>;
    unsafe {
         *ptr1;
         *ptr2;
    }
}
