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

import com.eed3si9n.expecty.Expecty
import expecty.Compat
import expecty.Compat.isScala3

object RenderingTest extends verify.BasicTestSuite {
  val assert1 = Expecty.assert

  test("literals") {
    outputs("""assertion failed
"abc".length() == 2
      |        |
      3        false
      """) {
      assert1 {
        "abc".length() == 2
      }
    }
  }

  test("List.apply") {
    if (Compat.scala == "3.0") {
      outputs("""assertion failed
List() == List(1, 2)
|      |  |
List() |  List(1, 2)
       false
    """) {
        assert1 {
          List() == List(1, 2)
        }
      }
    } else {
      outputs("""assertion failed
List() == List(1, 2)
       |  |
       |  List(1, 2)
       false
    """) {
        assert1 {
          List() == List(1, 2)
        }
      }
    }
  }

  test("List.apply2") {
    if (Compat.scala == "3.0") {
      outputs("""assertion failed
List(1, 2) == List()
|          |  |
List(1, 2) |  List()
           false
    """) {
        assert1 {
          List(1, 2) == List()
        }
      }
    } else {
      outputs("""assertion failed
List(1, 2) == List()
|          |
List(1, 2) false
    """) {
        assert1 {
          List(1, 2) == List()
        }
      }
    }
  }

  test("infix operators") {
    val str = "abc"

    outputs("""assertion failed
str + "def" == "other"
|   |       |
abc abcdef  false
    """) {
      assert1 {
        str + "def" == "other"
      }
    }
  }

  test("null value") {
    val x = null

    outputs("""assertion failed
x == "null"
| |
| false
null
    """) {
      assert1 {
        x == "null"
      }
    }
  }

  test("arithmetic expressions") {
    val one = 1

    outputs("""assertion failed
one + 2 == 4
|   |   |
1   3   false
    """) {
      assert1 {
        one + 2 == 4
      }
    }
  }

  test("field") {
    val person = Person()

    outputs("""assertion failed
person.age == 43
|      |   |
|      42  false
Person(Fred,42)
    """) {
      assert1 {
        person.age == 43
      }
    }
  }

  test("0-arity apply") {
    val person = Person()
    outputs("""assertion failed
person.doIt() == "pending"
|      |      |
|      done   false
Person(Fred,42)
    """) {
      assert1 {
        person.doIt() == "pending"
      }
    }
  }

  test("1-arity apply") {
    val person = Person()
    val word = "hey"

    outputs("""assertion failed
person.sayTwice(word) == "hoho"
|      |        |     |
|      heyhey   hey   false
Person(Fred,42)
    """) {
      assert1 {
        person.sayTwice(word) == "hoho"
      }
    }
  }

  test("2-arity apply") {
    val person = Person()
    val word1 = "hey"
    val word2 = "ho"

    outputs("""assertion failed
person.sayTwo(word1, word2) == "hoho"
|      |      |      |      |
|      heyho  hey    ho     false
Person(Fred,42)
    """) {
      assert1 {
        person.sayTwo(word1, word2) == "hoho"
      }
    }
  }

  test("varargs apply") {
    val person = Person()
    val word1 = "foo"
    val word2 = "bar"
    val word3 = "baz"

    outputs("""assertion failed
person.sayAll(word1, word2, word3) == "hoho"
|      |      |      |      |      |
|      |      foo    bar    baz    false
|      foobarbaz
Person(Fred,42)
    """) {
      assert1 {
        person.sayAll(word1, word2, word3) == "hoho"
      }
    }
  }

  test("nested apply") {
    val person = Person()

    outputs("""assertion failed
person.sayTwo(person.sayTwice(person.name), "bar") == "hoho"
|      |      |      |        |      |             |
|      |      |      FredFred |      Fred          false
|      |      Person(Fred,42) Person(Fred,42)
|      FredFredbar
Person(Fred,42)
    """) {
      assert1 {
        person.sayTwo(person.sayTwice(person.name), "bar") == "hoho"
      }
    }
  }

  test("constructor apply") {
    val brand = "BMW"
    val model = "M5"

    if (isScala3) {
      outputs("""assertion failed
Car(brand, model).brand == "Audi"
|   |      |            |
|   BMW    M5           false
BMW M5
    """) {
        assert1 {
          Car(brand, model).brand == "Audi"
        }
      }
    } else {
      outputs("""assertion failed
Car(brand, model).brand == "Audi"
|   |      |      |     |
|   BMW    M5     BMW   false
BMW M5
    """) {
        assert1 {
          Car(brand, model).brand == "Audi"
        }
      }
    }
  }

  test("method apply") {
    outputs("""assertion failed
something(0) == "something1"
|            |
something    false
    """) {
      assert1 {
        something(0) == "something1"
      }
    }
  }

  test("tuple") {
    if (isScala3) {
      outputs("""assertion failed
(1, 2)._1 == 3
 |     |  |
 (1,2) 1  false
      """) {
        assert1 {
          (1, 2)._1 == 3
        }
      }
    } else {
      outputs("""assertion failed
(1, 2)._1 == 3
|      |  |
(1,2)  1  false
      """) {
        assert1 {
          (1, 2)._1 == 3
        }
      }
    }
  }

  test("option") {
    outputs("""assertion failed
Some(23) == Some(22)
|        |  |
Some(23) |  Some(22)
         false
      """) {
      assert1 {
        Some(23) == Some(22)
      }
    }
  }

  test("message") {
    val person = Person()
    if (isScala3) {
      outputs("""assertion failed: something something
person.age == 43
|      |   |
|      42  false
Person(Fred,42)
      """) {
        assert1(person.age == 43, "something something")
      }
    } else {
      outputs("""assertion failed: something something
assert1(person.age == 43, "something something")
        |      |   |
        |      42  false
        Person(Fred,42)
      """) {
        assert1(person.age == 43, "something something")
      }
    }
  }

  test("long string") {
    val str1 = """virtue! a fig! 'tis in ourselves that we are thus or thus.
    |our bodies are our gardens, to the which our wills are gardeners: so that
    |if we will plant nettles, or sow lettuce, set hyssop and weed up thyme,
    |supply it with one gender of herbs, or distract it with many, either to
    |have it sterile with idleness, or manured with industry, why, the power
    |and corrigible authority of this lies in our wills.""".stripMargin

    val str2 = """a pig! 'tis in ourselves that we are thus or thus.
    |our bodies are our gardens, to the which our wills are gardeners; so that
    |if we will plant nettles, or sow cabbage, set hyssop and weed up thyme,
    |supply it with one gender of herbs, or distract it with many, either to
    |have it sterile with idleness, or manured with industry, why, the power
    |and corrigible authority of this lies in our wills.""".stripMargin

    if (isScala3) {
      outputs(
        """assertion failed: custom message
"virtue! " + str2
           | |
           | a pig! 'tis in ourselves that we are thus or thus....
           virtue! a pig! 'tis in ourselves that we are thus or thus....
virtue! a [fig]! 'tis in ourselves that we are thus or thus.                 |  virtue! a [pig]! 'tis in ourselves that we are thus or thus.
our bodies are our gardens, to the which our wills are gardeners[:] so that  |  our bodies are our gardens, to the which our wills are gardeners[;] so that
if we will plant nettles, or sow [lettuce], set hyssop and weed up thyme,    |  if we will plant nettles, or sow [cabbage], set hyssop and weed up thyme,
supply it with one gender of herbs, or distract it with many, either to      |  supply it with one gender of herbs, or distract it with many, either to
have it sterile with idleness, or manured with industry, why, the power      |  have it sterile with idleness, or manured with industry, why, the power
and corrigible authority of this lies in our wills.                          |  and corrigible authority of this lies in our wills.
      """
      ) {
        Expecty.assertEquals(str1, "virtue! " + str2, "custom message")
      }
    } else {
      outputs(
        """assertion failed: custom message
Expecty.assertEquals(str1, "virtue! " + str2, "custom message")
                                      | |
                                      | a pig! 'tis in ourselves that we are thus or thus....
                                      virtue! a pig! 'tis in ourselves that we are thus or thus....
virtue! a [fig]! 'tis in ourselves that we are thus or thus.                 |  virtue! a [pig]! 'tis in ourselves that we are thus or thus.
our bodies are our gardens, to the which our wills are gardeners[:] so that  |  our bodies are our gardens, to the which our wills are gardeners[;] so that
if we will plant nettles, or sow [lettuce], set hyssop and weed up thyme,    |  if we will plant nettles, or sow [cabbage], set hyssop and weed up thyme,
supply it with one gender of herbs, or distract it with many, either to      |  supply it with one gender of herbs, or distract it with many, either to
have it sterile with idleness, or manured with industry, why, the power      |  have it sterile with idleness, or manured with industry, why, the power
and corrigible authority of this lies in our wills.                          |  and corrigible authority of this lies in our wills.
      """
      ) {
        Expecty.assertEquals(str1, "virtue! " + str2, "custom message")
      }
    }
  }

  test("extension class with implicit parameters") {
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

    outputs("""assertion failed
"abc".length() === 2
      |        |
      3        false
      """) {
      assert1 {
        "abc".length() === 2
      }
    }
  }

  test("extension class without implicit parameters") {
    // Regression test for https://github.com/eed3si9n/expecty/issues/50

    implicit class EqOps[T](i: T) {
      def ===(other: T) = i == other
    }

    outputs("""assertion failed
"abc".length() === 2
      |        |
      3        false
      """) {
      assert1 {
        "abc".length() === 2
      }
    }
  }

  test("method with an implicit parameter") {
    trait Eq[T] {
      def eq(x: T, y: T): Boolean
    }

    object Eq {
      implicit val eqInt: Eq[Int] = new Eq[Int] {
        def eq(x: Int, y: Int) = (x - y) == 0
      }
    }

    class Hello {
      def compare[T: Eq](base: T, other: T) = implicitly[Eq[T]].eq(base, other)
      override def toString = "Hello"
    }

    val x = new Hello
    val y = 30

    outputs(
      """
      *assertion failed
      *
      *x.compare(15, y) == true
      *| |           |  |
      *| false       30 false
      *Hello
      """.stripMargin('*')
    ) {
      assert1 {
        x.compare(15, y) == true
      }
    }
  }

  def outputs(rendering: String)(expectation: => Unit): Unit = {
    def normalize(s: String) = s.trim().linesIterator.mkString

    try {
      expectation
      fail("Expectation should have failed but didn't")
    } catch {
      case e: AssertionError => {
        val expected = normalize(rendering)
        val actual = normalize(e.getMessage)
          .replaceAll("@[0-9a-f]*", "@\\.\\.\\.")
          .replaceAll("\u001b\\[[\\d;]*[^\\d;]", "")
        if (actual != expected) {
          throw new AssertionError(s"""Expectation output doesn't match: ${e.getMessage}
               %expected = 
               %$expected
               %actual   = 
               %$actual
               %""".stripMargin('%'))
        }
      }
    }
  }

  def something(x: Int): String = "something"

  case class Person(name: String = "Fred", age: Int = 42) {
    def doIt() = "done"
    def sayTwice(word: String) = word * 2
    def sayTwo(word1: String, word2: String) = word1 + word2
    def sayAll(words: String*) = words.mkString("")
  }

  case class Car(val brand: String, val model: String) {
    override def toString = brand + " " + model
  }

}
