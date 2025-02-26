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
 * @author scottjohnson@google.com (Scott Johnson)
 */
@RunWith(JUnit4.class)
public class ModifyingCollectionWithItselfTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(ModifyingCollectionWithItself.class, getClass());

  @Test
  public void positiveCases1() {
    compilationHelper
        .addSourceLines(
            "ModifyingCollectionWithItselfPositiveCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            import java.util.ArrayList;
            import java.util.List;

            /**
             * @author scottjohnson@google.com (Scott Johnson)
             */
            public class ModifyingCollectionWithItselfPositiveCases {

              List<Integer> a = new ArrayList<Integer>();
              List<Integer> c = new ArrayList<Integer>();

              public void addAll(List<Integer> b) {
                // BUG: Diagnostic contains: a.addAll(b)
                this.a.addAll(a);

                // BUG: Diagnostic contains: a.addAll(1, b)
                a.addAll(1, a);
              }

              public void containsAll(List<Integer> b) {
                // BUG: Diagnostic contains: this.a.containsAll(b)
                this.a.containsAll(this.a);

                // BUG: Diagnostic contains: a.containsAll(b)
                a.containsAll(this.a);
              }

              public void retainAll(List<Integer> a) {
                // BUG: Diagnostic contains: this.a.retainAll(a)
                a.retainAll(a);
              }

              public void removeAll() {
                // BUG: Diagnostic contains: a.clear()
                this.a.removeAll(a);

                // BUG: Diagnostic contains: a.clear()
                a.removeAll(a);
              }

              static class HasOneField {
                List<Integer> a;

                void removeAll() {
                  // BUG: Diagnostic contains: a.clear();
                  a.removeAll(a);
                }

                void testParameterFirst(List<Integer> b) {
                  // BUG: Diagnostic contains: this.a.removeAll(b);
                  b.removeAll(b);
                }

                void expressionStatementChecks() {
                  // BUG: Diagnostic contains: ModifyingCollectionWithItself
                  boolean b = 2 == 2 && a.containsAll(a);

                  // BUG: Diagnostic contains: ModifyingCollectionWithItself
                  b = a.retainAll(a);

                  // BUG: Diagnostic contains: ModifyingCollectionWithItself
                  b = a.removeAll(a);
                }
              }
            }\
            """)
        .doTest();
  }

  @Test
  public void negativeCase() {
    compilationHelper
        .addSourceLines(
            "ModifyingCollectionWithItselfNegativeCases.java",
            """
            package com.google.errorprone.bugpatterns.testdata;

            import java.util.ArrayList;
            import java.util.List;

            /**
             * @author scottjohnson@google.com (Scott Johnson)
             */
            public class ModifyingCollectionWithItselfNegativeCases {

              List<Integer> a = new ArrayList<Integer>();

              public boolean addAll(List<Integer> b) {
                return a.addAll(b);
              }

              public boolean removeAll(List<Integer> b) {
                return a.removeAll(b);
              }

              public boolean retainAll(List<Integer> b) {
                return a.retainAll(b);
              }

              public boolean containsAll(List<Integer> b) {
                return a.containsAll(b);
              }
            }\
            """)
        .doTest();
  }
}
