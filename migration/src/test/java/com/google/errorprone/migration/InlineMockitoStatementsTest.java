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

/** {@link InlineMockitoStatementsTest}Test */
@RunWith(JUnit4.class)
public class InlineMockitoStatementsTest {
  private final BugCheckerRefactoringTestHelper helper =
      BugCheckerRefactoringTestHelper.newInstance(InlineMockitoStatements.class, getClass());

  @Test
  public void migrateMockitoWhenThenReturnAndThenAnswer() {
    helper
        .addInputLines(
            "Foo.java",
            "package com.google.test;",
            "",
            "import com.google.errorprone.annotations.InlineMe;",
            "",
            "public final class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.getId_migrated())\")",
            "  public String getId() {",
            "    return String.valueOf(getId_migrated());",
            "  }",
            "",
            "  public Integer getId_migrated() {",
            "    return 1;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    when(foo.getId()).thenReturn(\"2\");",
            "",
            "    when(foo.getId()).thenReturn(\"2\", \"3\");",
            "",
            "    when(foo.getId()).thenAnswer(inv -> String.valueOf(\"1\"));",
            "  }",
            "}")
        .addOutputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    when(foo.getId()).thenReturn(\"2\");",
            "    when(foo.getId_migrated()).thenReturn(Integer.valueOf(\"2\"));",
            "",
            "    when(foo.getId()).thenReturn(\"2\", \"3\");",
            "    when(foo.getId_migrated()).thenReturn(Integer.valueOf(\"2\"), Integer.valueOf(\"3\"));",
            "",
            "    when(foo.getId()).thenAnswer(inv -> String.valueOf(\"1\"));",
            "    when(foo.getId_migrated()).thenAnswer(inv -> Integer.valueOf(String.valueOf(\"1\")));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void migrateMockitoDoReturnAndDoAnswer() {
    helper
        .addInputLines(
            "Foo.java",
            "package com.google.test;",
            "",
            "import com.google.errorprone.annotations.InlineMe;",
            "",
            "public final class Foo {",
            "  @Deprecated",
            "  @InlineMe(replacement = \"String.valueOf(this.getId_migrated())\")",
            "  public String getId() {",
            "    return String.valueOf(getId_migrated());",
            "  }",
            "",
            "  public Integer getId_migrated() {",
            "    return 1;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "import static org.mockito.Mockito.doReturn;",
            "import static org.mockito.Mockito.doAnswer;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    doReturn(\"2\").when(foo).getId();",
            //            "",
            //            "    when(foo.getId()).thenReturn(\"2\", \"3\");",
            //            "",
            //            "    when(foo.getId()).thenAnswer(inv -> String.valueOf(\"1\"));",
            "  }",
            "}")
        .addOutputLines(
            "BarTest.java",
            "package com.google.test;",
            "",
            "import org.junit.Test;",
            "import static org.mockito.Mockito.mock;",
            "import static org.mockito.Mockito.when;",
            "import static org.mockito.Mockito.doReturn;",
            "import static org.mockito.Mockito.doAnswer;",
            "",
            "public final class BarTest {",
            "  @Test",
            "  public void simpleTest() {",
            "    Foo foo = mock(Foo.class);",
            "    doReturn(\"2\").when(foo).getId();",
            "    doReturn(Integer.valueOf(\"2\")).when(foo).getId_migrated();",
            //            "",
            //            "    when(foo.getId()).thenReturn(\"2\", \"3\");",
            //            "    when(foo.getId_migrated()).thenReturn(Integer.valueOf(\"2\"),
            // Integer.valueOf(\"3\"));",
            //            "",
            //            "    when(foo.getId()).thenAnswer(inv -> String.valueOf(\"1\"));",
            //            "    when(foo.getId_migrated()).thenAnswer(inv ->
            // Integer.valueOf(String.valueOf(\"1\")));",
            "  }",
            "}")
        .doTest();
  }
}
