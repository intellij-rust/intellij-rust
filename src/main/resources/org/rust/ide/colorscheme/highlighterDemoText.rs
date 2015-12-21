fn main() {
    // A simple integer calculator:
    // `+` or `-` means add or subtract by 1
    // `*` or `/` means multiply or divide by 2

    let program = "+ + * - /";
    let mut <mut-binding>accumulator</mut-binding> = 0;

    for token in program.chars() {
        match token {
            '+' => <mut-binding>accumulator</mut-binding> += 1,
            '-' => <mut-binding>accumulator</mut-binding> -= 1,
            '*' => <mut-binding>accumulator</mut-binding> *= 2,
            '/' => <mut-binding>accumulator</mut-binding> /= 2,
            _ => { /* ignore everything else */ }
        }
    }

    <macro>println!</macro>("The program \"{}\" calculates the value {}",
             program, <mut-binding>accumulator</mut-binding>);
}
