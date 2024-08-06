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

import compat._
import scala.util.Properties

class RecorderMacro[C <: Context](val context: C) {
  import context.universe._

  /** captures a method invocation in the shape of assert(expr, message). */
  def apply[A: context.WeakTypeTag, R: context.WeakTypeTag](recording: context.Tree, message: context.Tree): Expr[R] = {
    context.Expr(
      Block(
        declareRuntime[A, R] ::
          recordMessage(message) ::
          recordExpressions(recording),
        completeRecording
      )
    )
  }

  def all[A: context.WeakTypeTag, R: context.WeakTypeTag](recordings: Seq[context.Tree]): Expr[R] = {
    context.Expr(
      Block(
        declareRuntime[A, R] ::
          recordings.toList.flatMap(recordExpressions),
        completeRecording
      )
    )
  }

  /** captures a method invocation in the shape of assertEquals(expected, found). */
  def apply2[A: context.WeakTypeTag, R: context.WeakTypeTag](
      expected: context.Tree,
      found: context.Tree,
      message: context.Tree
  ): Expr[R] = {
    context.Expr(
      Block(
        declareRuntime[A, R]("stringAssertEqualsListener") ::
          recordMessage(message) ::
          recordExpressions(expected) :::
          recordExpressions(found),
        completeRecording
      )
    )
  }

  private[this] def declareRuntime[A: context.WeakTypeTag, R: context.WeakTypeTag](listener: String): Tree = {
    val runtimeClass = context.mirror.staticClass(classOf[RecorderRuntime[_, _]].getName())
    ValDef(
      Modifiers(),
      termName(context)("$com_eed3si9n_expecty_recorderRuntime"),
      TypeTree(weakTypeOf[RecorderRuntime[A, R]]),
      Apply(
        Select(New(Ident(runtimeClass)), termNames.CONSTRUCTOR),
        List(Select(context.prefix.tree, termName(context)(listener)))
      )
    )
  }

  private[this] def declareRuntime[A: context.WeakTypeTag, R: context.WeakTypeTag]: Tree = {
    val runtimeClass = context.mirror.staticClass(classOf[RecorderRuntime[_, _]].getName())
    ValDef(
      Modifiers(),
      termName(context)("$com_eed3si9n_expecty_recorderRuntime"),
      TypeTree(weakTypeOf[RecorderRuntime[A, R]]),
      Apply(
        Select(New(Ident(runtimeClass)), termNames.CONSTRUCTOR),
        List(Select(context.prefix.tree, termName(context)("listener")))
      )
    )
  }

  private[this] def recordExpressions(recording: Tree): List[Tree] = {
    val source = getSourceCode(recording)
    val ast = showRaw(recording)
    try {
      List(resetValues, recordExpression(source, ast, recording))
    } catch {
      case e: Throwable =>
        throw new RuntimeException("Expecty: Error rewriting expression.\nText: " + source + "\nAST : " + ast, e)
    }
  }

  private[this] def recordMessage(message: Tree): Tree =
    Apply(
      Select(Ident(termName(context)("$com_eed3si9n_expecty_recorderRuntime")), termName(context)("recordMessage")),
      List(message)
    )

  private[this] def completeRecording: Tree =
    Apply(
      Select(Ident(termName(context)("$com_eed3si9n_expecty_recorderRuntime")), termName(context)("completeRecording")),
      List()
    )

  private[this] def resetValues: Tree =
    Apply(
      Select(Ident(termName(context)("$com_eed3si9n_expecty_recorderRuntime")), termName(context)("resetValues")),
      List()
    )

  // emit recorderRuntime.recordExpression(<source>, <tree>, instrumented)
  private[this] def recordExpression(source: String, ast: String, expr: Tree) = {
    val instrumented = recordAllValues(expr)
    log(
      expr,
      s"""
Expression      : ${source.trim()}
Original AST    : $ast
Instrumented AST: ${showRaw(instrumented)}")

    """
    )

    // to avoid missing interpolator lint, turn "a$b" into "a" + "$" + "b"
    val cleanSource = {
      val N = source.length - 1
      source.indexOf('$') match {
        case -1 | N => q"$source"
        case _ =>
          val parts = source.split('$').toList
          parts.tail.foldLeft(q"${parts.head}")((t, s) => q"""$t + "$$" + $s""")
      }
    }
    Apply(
      Select(Ident(termName(context)("$com_eed3si9n_expecty_recorderRuntime")), termName(context)("recordExpression")),
      List(cleanSource, q"$ast", instrumented, getSourceLocation)
    )
  }

  private[this] def splitExpressions(recording: Tree): List[Tree] =
    recording match {
      case Block(xs, y) => xs ::: List(y)
      case _            => List(recording)
    }

  private[this] def recordAllValues(expr: Tree): Tree =
    expr match {
      case New(_)     => expr // only record after ctor call
      case Literal(_) => expr // don't record
      // don't record value of implicit "this" added by compiler; couldn't find a better way to detect implicit "this" than via point
      case Select(x @ This(_), y) if getPosition(expr).point == getPosition(x).point => expr
      case x: Select if x.symbol.isModule        => expr // don't try to record the value of packages
      case Apply(_, _) if expr.symbol.isImplicit => recordSubValues(expr)
      case _ =>
        val sub = recordSubValues(expr)
        val res = recordValue(sub, expr)
        res

    }

  private[this] def recordSubValues(expr: Tree): Tree = {
    expr match {
      case Apply(Apply(x, y), z) if expr.symbol.isImplicit =>
        // case for implicit extensions that have implicit parameters.
        // Inner Apply is the application of the value the extension applies to.
        // Outer Apply is the application of implicit parameters
        Apply(Apply(x, y.map(recordAllValues)), z)
      case Apply(x, ys) =>
        val allParametersAreImplicit =
          ys.map(x => Option(x.symbol).fold(false)(_.isImplicit)).forall(_ == true)

        if (ys.nonEmpty && allParametersAreImplicit)
          Apply(recordSubValues(x), ys)
        else
          Apply(recordAllValues(x), ys.map(recordAllValues))
      case TypeApply(x, ys) => TypeApply(recordSubValues(x), ys)
      case Select(x, y)     => Select(recordAllValues(x), y)
      case _                => expr
    }

  }

  private[this] def recordValue(expr: Tree, origExpr: Tree): Tree = {
    if (origExpr.tpe.typeSymbol.isType) {
      Apply(
        Select(Ident(termName(context)("$com_eed3si9n_expecty_recorderRuntime")), termName(context)("recordValue")),
        List(expr, Literal(Constant(getAnchor(origExpr))))
      )
    } else expr
  }

  private[this] def getSourceCode(expr: Tree): String = getPosition(expr).lineContent

  private[this] def getAnchor(expr: Tree): Int =
    expr match {
      case Apply(x, ys)     => getAnchor(x) + 0
      case TypeApply(x, ys) => getAnchor(x) + 0
      case _ => {
        val pos = getPosition(expr)
        pos.point - pos.source.lineToOffset(pos.line - 1)
      }
    }

  private[this] def getPosition(expr: Tree) = expr.pos.asInstanceOf[scala.reflect.internal.util.Position]

  private[this] def log(expr: Tree, msg: => String): Unit = {
    if (Properties.propOrFalse("org.expecty.debug")) context.info(expr.pos, msg, force = false)
  }

  private def getSourceLocation = {
    import context.universe._

    val pwd = java.nio.file.Paths.get("").toAbsolutePath
    val p = context.enclosingPosition.source.path
    val abstractFile = context.enclosingPosition.source.file

    // Comparing roots to avoid the windows-specific edge case of relativisation crashing
    // because the CWD and the source file are in two different drives (C:/ and D:/ for instance).
    val rp = if (!abstractFile.isVirtual && pwd.getRoot() == abstractFile.file.toPath.getRoot()) {
      pwd.relativize(abstractFile.file.toPath()).toString()
    } else p

    val path = Literal(Constant(p))
    val relativePath = Literal(Constant(rp))
    val line = Literal(Constant(context.enclosingPosition.line))
    New(typeOf[Location], path, relativePath, line)
  }

}

object VarargsRecorderMacro {
  def apply[A: context.WeakTypeTag, R: context.WeakTypeTag](
      context: Context
  )(recordings: context.Tree*): context.Expr[R] = {
    new RecorderMacro[context.type](context).all[A, R](recordings)
  }
}

object RecorderMacro1 {
  def apply[A: context.WeakTypeTag, R: context.WeakTypeTag](
      context: Context
  )(recording: context.Tree): context.Expr[R] = {
    import context.universe._
    new RecorderMacro[context.type](context).apply[A, R](recording, q"""""""")
  }
}

object RecorderMacro {
  def apply[A: context.WeakTypeTag, R: context.WeakTypeTag](
      context: Context
  )(recording: context.Tree, message: context.Tree): context.Expr[R] = {
    new RecorderMacro[context.type](context).apply[A, R](recording, message)
  }
}

object StringRecorderMacro {
  def apply[A: context.WeakTypeTag, R: context.WeakTypeTag](
      context: Context
  )(expected: context.Tree, found: context.Tree): context.Expr[R] = {
    import context.universe._
    new RecorderMacro[context.type](context).apply2[A, R](expected, found, q"""""""")
  }
}

object StringRecorderMacroMessage {
  def apply[A: context.WeakTypeTag, R: context.WeakTypeTag](
      context: Context
  )(expected: context.Tree, found: context.Tree, message: context.Tree): context.Expr[R] = {
    new RecorderMacro[context.type](context).apply2[A, R](expected, found, message)
  }
}
