fn expressions() {
   // expressions
   1 >> 1;
   x >>= 1;
   x >= 1;

   // generics
   type T = Vec<Vec<_>>;
   let x: V<_>= ();
   let x: V<V<_>>= ();
   x.collect::<Vec<Vec<_>>>();

   // FIXME: ideally, this should be parse errors
   1 > > 1;
   x >> = 1;
   x > >= 1;
   x > = 1;
}