fn main() {
    #[foo]
    #[bar]
    let x = 1;

    let x = #[foo] #[bar]1;
    let _ = #[a] - #[b]-1;

    #[foo]
    #[bar]
    {}
}
