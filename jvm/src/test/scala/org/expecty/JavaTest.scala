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

import com.example.A
import com.example.Level

object JavaTest extends verify.BasicTestSuite {
  val assert1 = com.eed3si9n.expecty.Expecty.assert

  test("enum") {
    assert1(Level.LOW.toString() == "LOW")
  }

  test("static method") {
    assert1(A.getNumber() == 1)
  }
}
