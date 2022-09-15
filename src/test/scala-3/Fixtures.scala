package cats {
  package data {
    object Chain {
      def apply[A](a: A*): List[A] = List(a*)
    }
  }
}
