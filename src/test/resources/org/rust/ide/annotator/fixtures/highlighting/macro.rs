fn <info>main</info>() {
    <info>println!</info>["Hello, World!"];
    <info>unreachable!</info>();
}

<info>macro_rules!</info> foo {
    (x => $e:expr) => (println!("mode X: {}", $e));
    (y => $e:expr) => (println!("mode Y: {}", $e));
}

impl T {
    <info>foo!</info>();
}
