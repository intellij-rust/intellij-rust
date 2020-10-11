struct S {
    f: f64
}

fn main() {
    let S {mut f : f} = S { f: 92.0 };
    let S {,f} = S { f: 92.0 };

    let (a,, b);
    let (, c);

    let [a,, b];
    let [, c];
}
