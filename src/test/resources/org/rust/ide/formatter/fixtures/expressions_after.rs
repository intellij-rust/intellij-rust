struct S {
    foo: bool
}

fn main() {
    let x = S {
        foo: true
    };

    match x {
        S { foo } => {
            92;
        },

        S { foo } =>
            92
    }
}
