fn patterns() {
    let S {..} = x;
    let S {field} = x;
    let S {field,} = x;
    let S {field, ..} = x;
    let T(field, ..) = x;
    let T(.., field) = x;
    let (x, .., y) = (1, 2, 3, 4, 5);
    let &[x, ref y..] = [1, 2, 3];

    let ref a @ _ = value;

    if let Some(x,) = Some(92) { }

    let m!(x) = 92;

    let <i32>::foo ... <i32>::bar = 92;
    let Option::None = None;
}
