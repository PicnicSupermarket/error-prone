/*
 * Copyright 2014 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.bugpatterns;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author eaftan@google.com (Eddie Aftandilian)
 */
@RunWith(JUnit4.class)
public class ArrayHashCodeTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(ArrayHashCode.class, getClass());

  @Test
  public void positiveCase() {
    compilationHelper.addSourceFile("testdata/ArrayHashCodePositiveCases.java").doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper.addSourceFile("testdata/ArrayHashCodeNegativeCases.java").doTest();
  }

  /** Tests java.util.Objects hashCode methods, which are only in JDK 7 and above. */
  @Test
  public void java7NegativeCase() {
    compilationHelper.addSourceFile("testdata/ArrayHashCodeNegativeCases2.java").doTest();
  }
}
