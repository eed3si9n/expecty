# Expecty - Power Assertions for Scala

Expecty brings power assertions as known from [Groovy](http://groovy.codehaus.org) and [Spock](http://spockframework.org)
to the [Scala](http://scala-lang.org) language. It is a micro library that aims to do one thing well.

## License and Credits

Expecty was originally written by Peter Niederwieser, author of Spock.

Expecty is licensed under the Apache 2 license.

## Setup

[![expecty Scala version support](https://index.scala-lang.org/eed3si9n/expecty/expecty/latest-by-scala-version.svg?color=orange)](https://index.scala-lang.org/eed3si9n/expecty/expecty)

```scala
libraryDependencies += "com.eed3si9n.expecty" %% "expecty" % "<version (see above)>" % Test
```

| Scala Version | JVM | JS (1.x) | Native (0.4.x) |
| ------------- | :-: | :------: | :------------: |
| 3.1.x         | ✅  |   ✅     |     ✅         |
| 2.13.x        | ✅  |   ✅     |     ✅         |
| 2.12.x        | ✅  |   ✅     |     ✅         |
| 2.11.x        | ✅  |   ✅     |     ✅         |



## Code Examples

```scala
Welcome to Scala 2.12.15 (OpenJDK 64-Bit Server VM, Java 1.8.0_212).
Type in expressions for evaluation. Or try :help.

scala> import com.eed3si9n.expecty.Expecty.assert
import com.eed3si9n.expecty.Expecty.assert

scala> case class Person(name: String = "Fred", age: Int = 42) {
     |   def say(words: String*) = words.mkString(" ")
     | }
defined class Person

scala> val person = Person()
person: Person = Person(Fred,42)

scala> // Passing expectations

scala> assert(person.name == "Fred")

scala> assert(person.age * 2 == 84)

scala> assert(person.say("Hi", "from", "Expecty!") == "Hi from Expecty!")

scala> // Failing expectation

scala> val word1 = "ping"
word1: String = ping

scala> val word2 = "pong"
word2: String = pong

scala> assert(person.say(word1, word2) == "pong pong")
java.lang.AssertionError: assertion failed

assert(person.say(word1, word2) == "pong pong")
       |      |   |      |      |
       |      |   ping   pong   false
       |      ping pong
       Person(Fred,42)

  at com.eed3si9n.expecty.Expecty$ExpectyListener.expressionRecorded(Expecty.scala:35)
  at com.eed3si9n.expecty.RecorderRuntime.recordExpression(RecorderRuntime.scala:39)
  ... 36 elided

scala> assert(person.age * 2 == 73, "age is not right")
java.lang.AssertionError: assertion failed: age is not right

assert(person.age * 2 == 73, "age is not right")
       |      |   |   |
       |      42  84  false
       Person(Fred,42)

  at com.eed3si9n.expecty.Expecty$ExpectyListener.expressionRecorded(Expecty.scala:35)
  at com.eed3si9n.expecty.RecorderRuntime.recordExpression(RecorderRuntime.scala:39)
  ... 36 elided
```

## Further Examples

Have a look at [ExpectySpec.scala](https://github.com/eed3si9n/expecty/blob/master/jvm/src/test/scala/org/expecty/ExpectySpec.scala)
and other specs in the same directory.
