/*
 * Copyright 2018 The Error Prone Authors.
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

/** Test for ByteBufferBackingArray bug checker */
@RunWith(JUnit4.class)
public class ByteBufferBackingArrayTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(ByteBufferBackingArray.class, getClass());

  @Test
  public void positiveCases() {
    compilationHelper.addSourceFile("testdata/ByteBufferBackingArrayPositiveCases.java").doTest();
  }

  @Test
  public void negativeCases() {
    compilationHelper.addSourceFile("testdata/ByteBufferBackingArrayNegativeCases.java").doTest();
  }

  @Test
  public void i1004() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            import java.nio.ByteBuffer;

            public class Test {
              public void ByteBufferBackingArrayTest() {
                byte[] byteArray = ((ByteBuffer) new Object()).array();
              }
            }
            """)
        .doTest();
  }
}
