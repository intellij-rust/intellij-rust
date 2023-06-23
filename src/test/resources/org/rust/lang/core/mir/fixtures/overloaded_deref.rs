use std::rc::Rc;

fn foo(rc: Rc<i32>) {
    let a = *rc;
}
