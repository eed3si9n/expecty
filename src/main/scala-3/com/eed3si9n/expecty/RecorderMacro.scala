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
package com.eed3si9n.expecty

import scala.quoted._

class RecorderMacro(using qctx0: Quotes) {
  // https://github.com/lampepfl/dotty/releases/tag/3.0.0-M2
  // https://dotty.epfl.ch/docs/reference/metaprogramming/tasty-reflect.html#sealing-and-unsealing
  import qctx0.reflect._
  import util._

  private[this] val runtimeSym: Symbol = TypeRepr.of[RecorderRuntime[_, _]].typeSymbol

  def apply[A: Type, R: Type](
      recordings: Seq[Expr[A]],
      message: Expr[String],
      listener: Expr[RecorderListener[A, R]])(using qctx0: Quotes): Expr[R] = {
    val termArgs: Seq[Term] = recordings.map(_.asTerm.underlyingArgument)

    '{
      val recorderRuntime: RecorderRuntime[A, R] = new RecorderRuntime($listener)
      recorderRuntime.recordMessage($message)
      ${
        Block(
          termArgs.toList.flatMap(recordExpressions('{ recorderRuntime }.asTerm, _)),
          '{ recorderRuntime.completeRecording() }.asTerm
        ).asExprOf[R]
      }
    }
  }

  def apply2[A: Type, R: Type](
      expected: Expr[A],
      found: Expr[A],
      message: Expr[String],
      listener: Expr[RecorderListener[A, R]]): Expr[R] = {
    val expectedArg: Term = expected.asTerm.underlyingArgument
    val foundArg: Term = found.asTerm.underlyingArgument

    '{
      val recorderRuntime: RecorderRuntime[A, R] = new RecorderRuntime($listener)
      recorderRuntime.recordMessage($message)
      ${
        Block(
          recordExpressions('{ recorderRuntime }.asTerm, expectedArg) :::
          recordExpressions('{ recorderRuntime }.asTerm, foundArg),
          '{ recorderRuntime.completeRecording() }.asTerm
        ).asExprOf[R]
      }
    }
  }

  private[this] def getSourceLocation(expr: Tree) = {
    val pos = expr.pos

    val pwd  = java.nio.file.Paths.get("").toAbsolutePath
    val line = Expr(pos.endLine)

    val path = pos.sourceFile.jpath
    if (path != null){
      val file = path.toFile

      val pathExpr = Expr(path.toString)
      val relativePath = Expr(pwd.relativize(path).toString())
      val fileName = Expr(file.getName)

      '{Location(${pathExpr}, ${relativePath}, ${line})}.asTerm
    } else {
      '{Location("<virtual>", "<virtual>", ${line})}.asTerm
    }
  }

  private[this] def recordExpressions(runtime: Term, recording: Term): List[Term] = {
    val source = getSourceCode(recording)
    val ast = recording.show(using Printer.TreeStructure)
    val sourceLoc = getSourceLocation(recording)

    val resetValuesSel: Term = {
      val m = runtimeSym.memberMethod("resetValues").head
      runtime.select(m)
    }
    try {
      List(
        Apply(resetValuesSel, List()),
        recordExpression(runtime, source, ast, recording, sourceLoc)
      )
    } catch {
      case e: Throwable => throw new RuntimeException(
        "Expecty: Error rewriting expression.\nText: " + source + "\nAST : " + ast, e)
    }
  }

  // emit recorderRuntime.recordExpression(<source>, <tree>, instrumented)
  private[this] def recordExpression(runtime: Term, source: String, ast: String, expr: Term, loc: Term): Term = {
    val instrumented = recordAllValues(runtime, expr)
    val recordExpressionSel: Term = {
      val m = runtimeSym.memberMethod("recordExpression").head
      runtime.select(m)
    }
    Apply(recordExpressionSel,
      List(
        Literal(StringConstant(source)),
        Literal(StringConstant(ast)),
        instrumented,
        loc
      ))
  }

  private[this] def recordAllValues(runtime: Term, expr: Term): Term = {
    expr match {
      case New(_)     => expr
      case Literal(_) => expr
      case Typed(r @ Repeated(xs, y), tpe) => Typed.copy(r)(recordSubValues(runtime, r), tpe)
      // don't record value of implicit "this" added by compiler; couldn't find a better way to detect implicit "this" than via point
      case Select(x@This(_), y) if expr.pos.start == x.pos.start => expr
      // case x: Select if x.symbol.isModule => expr // don't try to record the value of packages
      case _ => recordValue(runtime, recordSubValues(runtime, expr), expr)
    }
  }

  private[this] def isImplicitParameter(t: Term): Boolean = 
    t.symbol.flags.is(Flags.Given) || t.symbol.flags.is(Flags.Implicit)

  private[this] def recordSubValues(runtime: Term, expr: Term): Term = {
    expr match {
      case Apply(a @ Apply(x, y), z) if isImplicitMethod(a) =>
        // case for implicit extensions that have implicit parameters.
        // Inner Apply is the application of the value the extension applies to.
        // Outer Apply is the application of implicit parameters
        try {
          Apply(Apply(x, y.map(recordAllValues(runtime, _))), z)
        } catch {
          case e: AssertionError => expr
        }
      case a @ Apply(x, ys) if isImplicitMethod(a) =>
        try {
          Apply(x, ys.map(recordAllValues(runtime, _)))
        } catch {
          case e: AssertionError => expr
        }
      case a @ Apply(x, ys) =>
        try {
  
          if(ys.nonEmpty && ys.forall(isImplicitParameter))
            Apply(recordSubValues(runtime, x), ys)
          else 
            Apply(recordSubValues(runtime, x), ys.map(recordAllValues(runtime, _)))
        } catch {
          case e: AssertionError => expr
        }
      // case TypeApply(x, ys) => recordValue(TypeApply.copy(expr)(recordSubValues(x), ys), expr)
      case TypeApply(x, ys) => TypeApply.copy(expr)(recordSubValues(runtime, x), ys)
      case Select(x, y)     => Select.copy(expr)(recordAllValues(runtime, x), y)
      case Typed(x, tpe)    => Typed.copy(expr)(recordSubValues(runtime, x), tpe)
      case Repeated(xs, y)  => Repeated.copy(expr)(xs.map(recordAllValues(runtime, _)), y)
      case _                => expr
    }
  }

  private[this] def isImplicitMethod(t : Apply): Boolean = {
    t.symbol.flags.is(Flags.Implicit)
  }

  private[this] def recordValue(runtime: Term, expr: Term, origExpr: Term): Term = {
    // debug
    // println("recording " + expr.showExtractors + " at " + getAnchor(expr))
    val recordValueSel: Term = {
      val m = runtimeSym.memberMethod("recordValue").head
      runtime.select(m)
    }
    def skipIdent(sym: Symbol): Boolean =
      sym match {
        case sym if sym.isDefDef => sym.signature.paramSigs.nonEmpty
        case _ =>
          sym.fullName match {
            case "scala" | "java" => true
            case fullName if fullName.startsWith("scala.") => true
            case fullName if fullName.startsWith("java.")  => true
            case _ => false
          }
      }

    def skipSelect(sym: Symbol): Boolean =
      (sym match {
        case sym if sym.isDefDef => skipIdent(sym)
        case sym if sym.isValDef => skipIdent(sym)
        case _ => true
      })
    expr match {
      case Select(_, _) if skipSelect(expr.symbol) => expr
      case TypeApply(_, _) => expr
      case Ident(_) if skipIdent(expr.symbol) => expr
      case a @ Apply(_, _) if isImplicitMethod(a) => expr
      case _ =>
        val tapply = recordValueSel.appliedToType(expr.tpe)
        Apply.copy(expr)(
          tapply,
          List(
            expr,
            Literal(IntConstant(getAnchor(expr)))
          )
        )
    }
  }

  private[this] def getSourceCode(expr: Tree): String = {
    val pos = expr.pos
    (" " * pos.startColumn) + pos.sourceCode.get
  }

  private[this] def getAnchor(expr: Term): Int =
    expr match {
      case Apply(x, ys) if x.symbol.fullName == "com.eed3si9n.expecty.RecorderRuntime.recordValue" && ys.nonEmpty =>
        getAnchor(ys.head)
      case Apply(x, ys)     => getAnchor(x) + 0
      case TypeApply(x, ys) => getAnchor(x) + 0
      case Select(x, y)     =>
        expr.pos.startColumn + math.max(0, expr.pos.sourceCode.get.indexOf(y))
      case _                => expr.pos.startColumn
    }
}

object RecorderMacro {
  def apply[A: Type, R: Type](
      recording: Expr[A],
      listener: Expr[RecorderListener[A, R]])(using qctx: Quotes): Expr[R] =
    new RecorderMacro().apply(Seq(recording), '{""}, listener)

  /** captures a method invocation in the shape of assert(expr, message). */
  def apply[A: Type, R: Type](
      recording: Expr[A],
      message: Expr[String],
      listener: Expr[RecorderListener[A, R]])(using qctx: Quotes): Expr[R] =
    new RecorderMacro().apply(Seq(recording), message, listener)

  def varargs[A: Type, R: Type](
      recordings: Expr[Seq[A]],
      listener: Expr[RecorderListener[A, R]])(using qctx: Quotes): Expr[R] = {
    import qctx.reflect._
    //!\ only works because we're expecting the macro to expand `R*`
    val Varargs(unTraversedRecordings) = recordings
    new RecorderMacro().apply(unTraversedRecordings, '{""}, listener)
  }
}

object StringRecorderMacro {
  /** captures a method invocation in the shape of assertEquals(expected, found). */
  def apply[R: Type](
      expected: Expr[String],
      found: Expr[String],
      listener: Expr[RecorderListener[String, R]])(using qctx: Quotes): Expr[R] =
    new RecorderMacro().apply2[String, R](expected, found, '{""}, listener)

  /** captures a method invocation in the shape of assertEquals(expected, found). */
  def apply[R: Type](
      expected: Expr[String],
      found: Expr[String],
      message: Expr[String],
      listener: Expr[RecorderListener[String, R]])(using qctx: Quotes): Expr[R] =
    new RecorderMacro().apply2[String, R](expected, found, message, listener)
}
