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

import language.experimental.macros

abstract class Recorder[R, A] {
  def listener: RecorderListener[R, A]
}

// format: off
trait UnaryRecorder[R, A] { self: Recorder[R, A] =>
  inline def apply(recording: R): A =
    ${ RecorderMacro.apply('recording, 'listener) }
  inline def apply(recording: R, message: => String): A =
    ${ RecorderMacro.apply('recording, 'message, 'listener) }
}

trait VarargsRecorder[R, A] { self: Recorder[R, A] =>
  inline def apply(inline recordings: R*): A =
    ${ RecorderMacro.varargs('recordings, 'listener)}
}
// format: on
