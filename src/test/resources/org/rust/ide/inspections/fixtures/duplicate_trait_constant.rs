trait T {
     const B: i32;
     const <error descr="Duplicate trait constant 'B'">B</error>: i32;
     const <error descr="Duplicate trait constant 'B'">B</error>: i32 = 1;
 }