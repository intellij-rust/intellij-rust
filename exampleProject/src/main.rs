fn main() {
    let xs = vec![1, 2, 3].into_iter()
        .map(|x| x * 2)
        .filter(|x| x > 2);

    let ys = vec![1,
                  2,
                  3].into_iter();

    let zs = concat(xs,
                    ys);

    let is_even = match zs.next {
        Some(x) => x % 2 == 0,
        None => false,
    };

    match is_even {
        true => {
            // Some block
        }
        _ => println("false"),
    };
    return;
}
