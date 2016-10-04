fn main() {
    let val1 = Box::new(84);
    drop(val1);

    let val2 = Box::new(84);
    <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">std::mem::drop(&val2)</warning>;
}

mod drop_ref {
    use std::mem::drop as free;

    fn test_drop() {
        let val = Box::new(84);
        <warning descr="Call to std::mem::drop with a reference argument. Dropping a reference does nothing">free(&val)</warning>;
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
