/*
 * Copyright 2021 The Error Prone Authors.
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

package com.google.errorprone.migration;

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CountMethodsTest {
  private final BugCheckerRefactoringTestHelper helper =
      BugCheckerRefactoringTestHelper.newInstance(CountMethods.class, getClass());

  @Test
  public void countSingleMethod() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Flowable;",
            "public final class Foo {",
            "  public Flowable<String> test() {",
            "    return Flowable.just(\"\");",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void countMethodReference() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Flowable;",
            "public final class Foo {",
            "  public Flowable<Object> test() {",
            "    return Flowable.just(\"1\").map(Flowable::just);",
            "  }",
            "}")
        .expectUnchanged()
        .doTest();
  }
}
