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
 * @author lowasser@google.com (Louis Wasserman)
 */
@RunWith(JUnit4.class)
public class EqualsNaNTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(EqualsNaN.class, getClass());

  @Test
  public void positiveCase() {
    compilationHelper
        .addSourceLines(
            "EqualsNaNPositiveCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            /**
             * @author lowasser@google.com (Louis Wasserman)
             */
            public class EqualsNaNPositiveCases {

              // BUG: Diagnostic contains: Double.isNaN(0.0)
              static final boolean ZERO_DOUBLE_NAN = 0.0 == Double.NaN;

              // BUG: Diagnostic contains: !Double.isNaN(1.0)
              static final boolean ONE_NOT_DOUBLE_NAN = Double.NaN != 1.0;

              // BUG: Diagnostic contains: Float.isNaN(2.f)
              static final boolean TWO_FLOAT_NAN = 2.f == Float.NaN;

              // BUG: Diagnostic contains: !Float.isNaN(3.0f)
              static final boolean THREE_NOT_FLOAT_NAN = 3.0f != Float.NaN;

              // BUG: Diagnostic contains: Double.isNaN(Double.NaN)
              static final boolean NAN_IS_NAN = Double.NaN == Double.NaN;

              // BUG: Diagnostic contains: Double.isNaN(123456)
              static final boolean INT_IS_NAN = 123456 == Double.NaN;
            }\
            """)
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "EqualsNaNNegativeCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            /**
             * @author lowasser@google.com (Louis Wasserman)
             */
            public class EqualsNaNNegativeCases {
              static final boolean NAN_AFTER_MATH = (0.0 / 0.0) == 1.0;
              static final boolean NORMAL_COMPARISON = 1.0 == 2.0;
            }\
            """)
        .doTest();
  }
}
