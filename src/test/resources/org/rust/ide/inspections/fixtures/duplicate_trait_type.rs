trait T {
    type A;
    type <error descr="Duplicate associated type 'A'">A</error>;
    type <error descr="Duplicate associated type 'A'">A</error>: bool;
}
