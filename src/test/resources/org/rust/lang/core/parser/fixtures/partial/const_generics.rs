fn foo<const N: bool = 1 > 0>() {}
fn var<const N: usize = 1 >> 3>() {}
fn main() {
    foo::<1 > 0>();
    bar::<1 >> 3>();
}
