impl_rdp! {
    grammar! {
        expression = _{ paren ~ expression? }
        paren      =  { ["("] ~ expression? ~ [")"] }
    }
}
