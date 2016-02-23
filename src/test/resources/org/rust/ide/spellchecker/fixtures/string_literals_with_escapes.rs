fn main() {
    let s = "Hello, <TYPO descr="Typo: In word 'Wodlr'">W\u{6F}dlr</TYPO>!";
    let s = "Hello, <TYPO descr="Typo: In word 'Wodlr'">W\x6Fdlr</TYPO>!";
}
