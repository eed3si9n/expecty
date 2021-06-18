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

object ExpectyTest extends verify.BasicTestSuite {
  import com.eed3si9n.expecty.Expecty.expect
  val assert1 = com.eed3si9n.expecty.Expecty.assert

  import java.lang.AssertionError

  val name = "Hi from Expecty!"

  test("passingExpectation") {
    assert1(name.length == 16)
  }

  test("failingExpectation") {
    intercept[AssertionError] {
      assert1(name.length() == 10)
    }
  }

  test("multiplePassingExpectations") {
    assert1(name.length == 16)
    assert1(name.startsWith("Hi"))
    assert1(name.endsWith("Expecty!"))
  }

  test("mixedPassingAndFailingExpectations") {
    intercept[AssertionError] {
      assert1(name.length == 16)
      assert1(name.startsWith("Ho"))
      assert1(name.endsWith("Expecty!"))
    }
  }

  test("extensionClassWithImplicitParams") {
    // Regression test for https://github.com/eed3si9n/expecty/issues/50
    trait Eq[T] {
      def eq(x: T, y: T): Boolean
    }

    object Eq {
      implicit val eqInt: Eq[Int] = new Eq[Int] {
        def eq(x: Int, y: Int) = (x - y) == 0
      }
    }

    implicit class EqOps[T](i: T)(implicit eq: Eq[T]) {
      def ===(other: T) = eq.eq(i, other)
    }

    assert1("abc".length() === 3)
  }

  test("extensionClassWithoutImplicitParams") {
    implicit class EqOps[T](i: T) {
      def ===(other: T) = i == other
    }

    assert1("abc".length() === 3)

  }

  test("Method with implicit parameter") {
    trait Test[T] {
      def a: T
    }

    implicit val testInt: Test[Int] = new Test[Int] {
      def a = 25
    }

    trait Data {
      def hello[T: Test](b: T) = (implicitly[Test[T]].a, b)
    }

    val z = new Data {}

    assert1(z.hello[Int](50) == 25 -> 50)
  }
}
