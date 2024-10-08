/*
 * Copyright 2017 The Error Prone Authors.
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

package com.google.errorprone.bugpatterns.overloading;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test cases for {@link InconsistentOverloads}.
 *
 * @author hanuszczak@google.com (Łukasz Hanuszczak)
 */
@RunWith(JUnit4.class)
public final class InconsistentOverloadsTest {
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(InconsistentOverloads.class, getClass());

  @Test
  public void inconsistentOverloadsNegativeCases() {
    compilationHelper.addSourceFile("testdata/InconsistentOverloadsNegativeCases.java").doTest();
  }

  @Test
  public void inconsistentOverloadsPositiveCasesAnnotations() {
    compilationHelper
        .addSourceFile("testdata/InconsistentOverloadsPositiveCasesAnnotations.java")
        .doTest();
  }

  @Test
  public void inconsistentOverloadsPositiveCasesGeneral() {
    compilationHelper
        .addSourceFile("testdata/InconsistentOverloadsPositiveCasesGeneral.java")
        .doTest();
  }

  @Test
  public void inconsistentOverloadsPositiveCasesGenerics() {
    compilationHelper
        .addSourceFile("testdata/InconsistentOverloadsPositiveCasesGenerics.java")
        .doTest();
  }

  @Test
  public void inconsistentOverloadsPositiveCasesInterleaved() {
    compilationHelper
        .addSourceFile("testdata/InconsistentOverloadsPositiveCasesInterleaved.java")
        .doTest();
  }

  @Test
  public void inconsistentOverloadsPositiveCasesSimple() {
    compilationHelper
        .addSourceFile("testdata/InconsistentOverloadsPositiveCasesSimple.java")
        .doTest();
  }

  @Test
  public void inconsistentOverloadsPositiveCasesVarargs() {
    compilationHelper
        .addSourceFile("testdata/InconsistentOverloadsPositiveCasesVarargs.java")
        .doTest();
  }

  @Test
  public void inconsistentOverloadsOverrides() {
    compilationHelper
        .addSourceFile("testdata/InconsistentOverloadsPositiveCasesOverrides.java")
        .doTest();
  }

  @Test
  public void suppressOnMethod() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            class Test {
              public void foo(Object object) {}

              @SuppressWarnings("InconsistentOverloads")
              public void foo(int i, Object object) {}
            }
            """)
        .doTest();
  }
}
