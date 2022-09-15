/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package foo

object ExpectyScala3Test extends verify.BasicTestSuite {
  import com.eed3si9n.expecty.Expecty.expect
  val assert1 = com.eed3si9n.expecty.Expecty.assert

  import java.lang.AssertionError

  val name = "Hi from Expecty!"

  trait Eq[T]:
    def eq(x: T, y: T): Boolean

  object Eq:
    given Eq[Int] with
      def eq(x: Int, y: Int) = (x - y) == 0

  test("scala 3 extension (1)") {
    // Regression test for https://github.com/eed3si9n/expecty/issues/50
    extension [T](i: T)(using eq: Eq[T]) def ===(other: T) = eq.eq(i, other)

    assert1("abc".length() === 3)
  }

  test("scala 3 extension (2)") {
    // Regression test for https://github.com/eed3si9n/expecty/issues/50
    extension [T](i: T) def ===(other: T)(using eq: Eq[T]) = eq.eq(i, other)

    assert1("abc".length() === 3)
  }

  test("Method with implicit parameter") {
    trait Test[T]:
      def a: T

    given ti: Test[Int] with
      def a = 25

    val ti2 = new Test[Int]:
      def a = 50

    trait Data:
      def hello[T: Test](b: T) = (summon[Test[T]].a, b)
      override def toString = "Data"

    val z = new Data {}

    assert1(z.hello(50)(using ti2) == 50 -> 50)
    assert1(z.hello(128) == 25 -> 128)
  }

  test("Capturing package names correctly") {
    assert1(cats.data.Chain(1, 2, 3).size == 3)
  }
}
