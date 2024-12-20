/*
 * Copyright 2015 The Error Prone Authors.
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
 * @author sebastian.h.monte@gmail.com (Sebastian Monte)
 */
@RunWith(JUnit4.class)
public class AssertFalseTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(AssertFalse.class, getClass());

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "AssertFalseNegativeCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            /**
             * @author sebastian.h.monte@gmail.com (Sebastian Monte)
             */
            public class AssertFalseNegativeCases {

              public void assertTrue() {
                assert true;
              }

              public void assertFalseFromCondition() {
                assert 0 == 1;
              }
            }\
            """)
        .doTest();
  }

  @Test
  public void positiveCase() {
    compilationHelper
        .addSourceLines(
            "AssertFalsePositiveCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            /**
             * @author sebastian.h.monte@gmail.com (Sebastian Monte)
             */
            public class AssertFalsePositiveCases {
              public void assertFalse() {
                // BUG: Diagnostic contains: throw new AssertionError()
                assert false;
              }
            }\
            """)
        .doTest();
  }
}
