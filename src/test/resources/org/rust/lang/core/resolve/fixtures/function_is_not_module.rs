fn foo(bar: usize) {}

fn main() {
    foo::<caret>bar // Yep, we used to resolve this!
}
