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

object RecorderMacro {

  def apply[R: Type, A: Type](
      recording: Expr[R],
      listener: Expr[RecorderListener[R, A]])(using qctx: Quotes): Expr[A] = {
    apply(recording, '{""}, listener)
  }

  def apply[R: Type, A: Type](
      recording: Expr[R],
      message: Expr[String],
      listener: Expr[RecorderListener[R, A]])(using qctx: Quotes): Expr[A] = {
    apply(Seq(recording), message, listener)
  }

  def varargs[R: Type, A: Type](
      recordings: Expr[Seq[R]],
      listener: Expr[RecorderListener[R, A]])(using qctx: Quotes): Expr[A] = {
    //!\ only works because we're expecting the macro to expand `R*`
    val Varargs(unTraversedRecordings) = recordings
    apply(unTraversedRecordings, '{""}, listener)
  }

  def apply[R: Type, A: Type](
      recordings: Seq[Expr[R]],
      message: Expr[String],
      listener: Expr[RecorderListener[R, A]])(using qctx0: Quotes): Expr[A] = {
    import qctx0.reflect._
    import util._
    val termArgs: Seq[Term] = recordings.map(_.asTerm.underlyingArgument)

    def getText(expr: Tree): String = {
      val pos = expr.pos
      (" " * pos.startColumn) + pos.sourceCode.get
    }

    def getSourceLocation(expr: Tree) = {
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


    '{
      // import qctx0.reflect._
      val recorderRuntime: RecorderRuntime[R, A] = new RecorderRuntime($listener)
      recorderRuntime.recordMessage($message)
      ${
        val runtimeSym = TypeRepr.of[RecorderRuntime[_, _]].typeSymbol match {
          case sym if sym.isClassDef => sym
        }
        val recordExpressionSel: Term = {
          val m = runtimeSym.memberMethod("recordExpression").head
          '{ recorderRuntime }.asTerm.select(m)
        }
        val recordValueSel: Term = {
          val m = runtimeSym.memberMethod("recordValue").head
          '{ recorderRuntime }.asTerm.select(m)
        }

        def recordExpressions(recording: Term): List[Term] = {
          val text = getText(recording)
          val ast = recording.show(using Printer.TreeStructure)
          val sourceLoc = getSourceLocation(recording)
          try {
            List(
              '{ recorderRuntime.resetValues() }.asTerm,
              recordExpression(text, ast, recording, sourceLoc)
            )
          } catch {
            case e: Throwable => throw new RuntimeException(
              "Expecty: Error rewriting expression.\nText: " + text + "\nAST : " + ast, e)
          }
        }

        def recordExpression(text: String, ast: String, expr: Term, loc: Term): Term = {
          val instrumented = recordAllValues(expr)
          Apply(recordExpressionSel,
            List(
              Literal(StringConstant(text)),
              Literal(StringConstant(ast)),
              instrumented,
              loc
            ))
        }

        def recordAllValues(expr: Term): Term =
          expr match {
            case New(_)     => expr
            case Literal(_) => expr
            case Typed(r @ Repeated(xs, y), tpe) => Typed.copy(r)(recordSubValues(r), tpe)
            // don't record value of implicit "this" added by compiler; couldn't find a better way to detect implicit "this" than via point
            case Select(x@This(_), y) if expr.pos.start == x.pos.start => expr
            // case x: Select if x.symbol.isModule => expr // don't try to record the value of packages
            case _ => recordValue(recordSubValues(expr), expr)
          }

        def recordSubValues(expr: Term): Term =
          expr match {
            case Apply(x, ys) =>
              try {
                Apply(recordAllValues(x), ys.map(recordAllValues))
              } catch {
                case e: AssertionError => expr
              }
            // case TypeApply(x, ys) => recordValue(TypeApply.copy(expr)(recordSubValues(x), ys), expr)
            case TypeApply(x, ys) => TypeApply.copy(expr)(recordSubValues(x), ys)
            case Select(x, y)     => Select.copy(expr)(recordAllValues(x), y)
            case Typed(x, tpe)    => Typed.copy(expr)(recordSubValues(x), tpe)
            case Repeated(xs, y)  => Repeated.copy(expr)(xs.map(recordAllValues), y)
            case _                => expr
          }

        def recordValue(expr: Term, origExpr: Term): Term = {
          // debug
          // println("recording " + expr.showExtractors + " at " + getAnchor(expr))

          def skipIdent(sym: Symbol): Boolean =
            sym.fullName match {
              case "scala" | "java" => true
              case fullName if fullName.startsWith("scala.") => true
              case fullName if fullName.startsWith("java.")  => true
              case _ => false
            }

          def skipSelect(sym: Symbol): Boolean =
            (sym match {
              case sym if sym.isDefDef => sym.signature.paramSigs.nonEmpty
              case sym if sym.isValDef => skipIdent(sym)
              case _ => true
            })
          expr match {
            case Select(_, _) if skipSelect(expr.symbol) => expr
            case TypeApply(_, _) => expr
            case Ident(_) if skipIdent(expr.symbol) => expr
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

        def getAnchor(expr: Term): Int =
          expr match {
            case Apply(x, ys) if x.symbol.fullName == "com.eed3si9n.expecty.RecorderRuntime.recordValue" && ys.nonEmpty =>
              getAnchor(ys.head)
            case Apply(x, ys)     => getAnchor(x) + 0
            case TypeApply(x, ys) => getAnchor(x) + 0
            case Select(x, y)     =>
              expr.pos.startColumn + math.max(0, expr.pos.sourceCode.get.indexOf(y))
            case _                => expr.pos.startColumn
          }

        Block(
          termArgs.toList.flatMap(recordExpressions),
          '{ recorderRuntime.completeRecording() }.asTerm
        ).asExprOf[A]
      }
    }
  }
}
