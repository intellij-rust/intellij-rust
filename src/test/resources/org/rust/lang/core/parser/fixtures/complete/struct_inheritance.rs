struct A;
struct B : A;
struct C : A, B;
struct D : A + B;
struct E(i32) : A;
struct F : A { x: i32 }
struct G : A where A: B;
struct H(i32) : A where A: B;
struct I : A where A: B { x: i32 }
