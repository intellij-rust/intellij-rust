struct S { <caret>foo: i32 }

fn main() {
    let x = S { foo: 92 };
    println!("{}", x.foo);
}
