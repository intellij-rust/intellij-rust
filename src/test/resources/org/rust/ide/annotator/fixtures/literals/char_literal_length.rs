fn main() {
    let ch1 = <error descr="empty char literal">''</error>;
    let ch2 = <error descr="too many characters in char literal">'abrakadabra'</error>;
    let ch3 = <error descr="too many characters in byte literal">b'abrakadabra'</error>;
    let ch4 = 'a';
    let ch5 = b'a';
}

