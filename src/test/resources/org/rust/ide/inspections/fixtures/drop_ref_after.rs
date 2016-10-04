fn main() {
    let val1 = Box::new(84);
    drop(val1);

    let val2 = Box::new(84);
    std::mem::drop(&val2);
}

mod drop_ref {
    use std::mem::drop as free;

    fn test_drop() {
        let val = Box::new(84);
        free(&val);
    }
}

mod shadow_drop_ref {

    fn drop(val: &Box<u32>) {
    }

    fn test_drop() {
        let val = Box::new(84);
        drop(&val); // This is the local drop
    }
}
