/*
 * Copyright 2022 The Error Prone Authors.
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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link IdentityConversion}. */
@RunWith(JUnit4.class)
public class IdentityConversionTest {
  private final BugCheckerRefactoringTestHelper helper =
      BugCheckerRefactoringTestHelper.newInstance(IdentityConversion.class, getClass());

  @Test
  public void removeValueOf() {
    helper
        .addInputLines(
            "Foo.java",
            "public final class Foo {",
            "  public void foo() {",
            "    Byte b1 = Byte.valueOf((Byte) Byte.MIN_VALUE);",
            "    Byte b2 = Byte.valueOf(Byte.MIN_VALUE);",
            "    byte b3 = Byte.valueOf((Byte) Byte.MIN_VALUE);",
            "    byte b4 = Byte.valueOf(Byte.MIN_VALUE);",
            "",
            "    Character c1 = Character.valueOf((Character) 'a');",
            "    Character c2 = Character.valueOf('a');",
            "    char c3 = Character.valueOf((Character)'a');",
            "    char c4 = Character.valueOf('a');",
            "",
            "    Integer i1 = Integer.valueOf((Integer) 1);",
            "    Integer i2 = Integer.valueOf(1);",
            "    int i3 = Integer.valueOf((Integer) 1);",
            "    int i4 = Integer.valueOf(1);",
            "",
            "    String s1 = String.valueOf(0);",
            "    String s2 = String.valueOf(\"1\");",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "public final class Foo {",
            "  public void foo() {",
            "    Byte b1 = (Byte) Byte.MIN_VALUE;",
            "    Byte b2 = Byte.valueOf(Byte.MIN_VALUE);",
            "    byte b3 = Byte.valueOf((Byte) Byte.MIN_VALUE);",
            "    byte b4 = Byte.MIN_VALUE;",
            "",
            "    Character c1 = (Character) 'a';",
            "    Character c2 = Character.valueOf('a');",
            "    char c3 = Character.valueOf((Character)'a');",
            "    char c4 = 'a';",
            "",
            "    Integer i1 = (Integer) 1;",
            "    Integer i2 = Integer.valueOf(1);",
            "    int i3 = Integer.valueOf((Integer) 1);",
            "    int i4 = 1;",
            "",
            "    String s1 = String.valueOf(0);",
            "    String s2 = \"1\";",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void removeCopyOf() {
    helper
        .addInputLines(
            "Foo.java",
            "import com.google.common.collect.ImmutableList;",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.ArrayList;",
            "",
            "public final class Foo {",
            "  public void foo() {",
            "    ImmutableSet<Object> set1 = ImmutableSet.copyOf(ImmutableSet.of());",
            "    ImmutableSet<Object> set2 = ImmutableSet.copyOf(ImmutableList.of());",
            "",
            "    ImmutableList<Integer> list1 = ImmutableList.copyOf(ImmutableList.of(1));",
            "    ImmutableList<Integer> list2 = ImmutableList.copyOf(new ArrayList<>(ImmutableList.of(1)));",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import com.google.common.collect.ImmutableList;",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.ArrayList;",
            "",
            "public final class Foo {",
            "  public void foo() {",
            "    ImmutableSet<Object> set1 = ImmutableSet.of();",
            "    ImmutableSet<Object> set2 = ImmutableSet.copyOf(ImmutableList.of());",
            "",
            "    ImmutableList<Integer> list1 = ImmutableList.of(1);",
            "    ImmutableList<Integer> list2 = ImmutableList.copyOf(new ArrayList<>(ImmutableList.of(1)));",
            "  }",
            "}")
        .doTest();
  }
}
