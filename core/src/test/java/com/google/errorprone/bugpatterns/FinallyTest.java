/*
 * Copyright 2013 The Error Prone Authors.
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
 * @author cushon@google.com (Liam Miller-Cushon)
 */
@RunWith(JUnit4.class)
public class FinallyTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(Finally.class, getClass());

  @Test
  public void positiveCase1() {
    compilationHelper.addSourceFile("testdata/FinallyPositiveCase1.java").doTest();
  }

  @Test
  public void positiveCase2() {
    compilationHelper.addSourceFile("testdata/FinallyPositiveCase2.java").doTest();
  }

  @Test
  public void negativeCase1() {
    compilationHelper.addSourceFile("testdata/FinallyNegativeCase1.java").doTest();
  }

  @Test
  public void negativeCase2() {
    compilationHelper.addSourceFile("testdata/FinallyNegativeCase2.java").doTest();
  }

  @Test
  public void lambda() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            class Test {
              void f() {
                try {
                } catch (Throwable t) {
                } finally {
                  Runnable r =
                      () -> {
                        return;
                      };
                }
              }
            }
            """)
        .doTest();
  }
}
