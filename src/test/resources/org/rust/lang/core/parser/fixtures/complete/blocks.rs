fn main() {
    let simple_block = {
        123
    };

    let block_with_label = 'block: {
        if foo() { break 'block 1; }
        if bar() { break 'block 2; }
        3
    };

    match 123 {
        1 => {},
        2 => 'b: { break 'b; },
        _ => {}
    }
}
