fn patterns() {
    let S {..} = x;
    let S {field} = x;
    let S {field,} = x;
    let S {field, ..} = x;

    let ref a @ _ = value;

    let m!(x) = 92;
}
