/*
 * Copyright 2012 The Error Prone Authors.
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
public class FutureReturnValueIgnoredTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(FutureReturnValueIgnored.class, getClass());

  @Test
  public void positiveCases() {
    compilationHelper.addSourceFile("testdata/FutureReturnValueIgnoredPositiveCases.java").doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper.addSourceFile("testdata/FutureReturnValueIgnoredNegativeCases.java").doTest();
  }

  @Test
  public void classAnnotationButCanIgnoreReturnValue() {
    compilationHelper
        .addSourceLines(
            "lib/Lib.java",
            """
            package lib;

            public class Lib {
              @com.google.errorprone.annotations.CanIgnoreReturnValue
              public static java.util.concurrent.Future<?> f() {
                return null;
              }
            }
            """)
        .addSourceLines(
            "Test.java",
            """
            class Test {
              void m() {
                lib.Lib.f();
              }
            }
            """)
        .doTest();
  }

  @Test
  public void completableFutureReturnValue() {
    compilationHelper
        .addSourceLines(
            "test.java",
            """
            import java.util.concurrent.CompletableFuture;

            class Test {
              void f(CompletableFuture<?> cf) {
                cf.exceptionally(t -> null);
              }
            }
            """)
        .doTest();
  }

  @Test
  public void completableFutureReturnValueJdk9() {
    compilationHelper
        .addSourceLines(
            "test.java",
            """
            import java.util.concurrent.CompletableFuture;
            import static java.util.concurrent.TimeUnit.MILLISECONDS;

            class Test {
              void f(CompletableFuture<?> cf) {
                cf.completeAsync(() -> null);
                cf.completeAsync(() -> null, null);
                cf.orTimeout(0, MILLISECONDS);
                cf.completeOnTimeout(null, 0, MILLISECONDS);
              }
            }
            """)
        .doTest();
  }
}
