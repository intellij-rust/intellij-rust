struct S<T>;

fn main() {
    let _: S::<caret>T = unreachable!();
}
