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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import io.reactivex.Single;
import java.util.function.Function;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Locale;

/** {@link AddDefaultMethod}Test */
@RunWith(JUnit4.class)
public class AddDefaultMethodTest {
  private final BugCheckerRefactoringTestHelper helper =
      BugCheckerRefactoringTestHelper.newInstance(AddDefaultMethod.class, getClass());

  @Test
  public void createBanner() {
    helper
        .addInputLines(
            "BannerInterface.java",
            "public interface BannerInterface {",
            "  String getBannerId();",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Banner.java",
            "import javax.annotation.concurrent.Immutable;",
            "@Immutable",
            "public final class Banner implements BannerInterface {",
            "  private final String bannerId; ",
            "",
            "  public Banner(String bannerId) {",
            "    this.bannerId = bannerId;",
            "  }",
            "",
            "  @Override",
            "  public String getBannerId() {",
            "    return bannerId;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BannerRequest.java",
            "import javax.annotation.concurrent.Immutable;",
            "",
            "@Immutable",
            "public final class BannerRequest implements BannerInterface {",
            "  private final String bannerId; ",
            "",
            "  public BannerRequest(String bannerId) {",
            "    this.bannerId = bannerId;",
            "  }",
            "",
            "  @Override",
            "  public String getBannerId() {",
            "    return bannerId;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines(
            "BannerService.java",
            "import io.reactivex.Single;",
            "",
            "public interface BannerService {",
            "  Single<Banner> createBanner(BannerRequest bannerRequest);",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void positive_singleToMonoClassMigration() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "public final class Foo {",
            "  public Single<String> bar() {",
            "    return Single.just(\"value\");",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public final class Foo {",
            "  @Deprecated",
            "  public Single<String> bar() {",
            "    return Single.just(\"value\");",
            "  }",
            "  public Mono<String> bar_migrated() {",
            "    return Single.just(\"value\").as(RxJava2Adapter::singleToMono);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void firstParameterChangeForClass() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "public final class Foo {",
            "  public Single<String> bar(String bannerId, Integer testName) {",
            "    return Single.just(bannerId);",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public final class Foo {",
            "  @Deprecated",
            "  public Single<String> bar(String bannerId, Integer testName) {",
            "    return Single.just(bannerId);",
            "  }",
            "  public Mono<String> bar_migrated(String stringName, Integer testName) {",
            "    return Single.just(bannerId).as(RxJava2Adapter::singleToMono);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void interfaceParametersChange() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "public interface Foo {",
            "  String bar(String bannerId, Integer testName);",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "public interface Foo {",
            "  @Deprecated",
            "  default java.lang.String bar(java.lang.String bannerId, java.lang.Integer testName) {",
            "    return String.valueOf(bar_migrated(bannerId, testName));",
            "  }",
            "",
            "  default java.lang.Integer bar_migrated(String bannerId, Integer testName) {",
            "    return Integer.valueOf(bar(bannerId, testName));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void supportMultiLineBlockWithOneReturn() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "public final class Foo {",
            "  public String bar(String name) {",
            "    Integer test = 1 + 1;",
            "    return String.valueOf(test) + name;",
            "  }",
            "}")
        .addOutputLines(
            "import io.reactivex.Single;",
            "public final class Foo {",
            "  @Deprecated",
            "  public String bar(String name) {",
            "    Integer test = 1 + 1;",
            "    return String.valueOf(test) + name;",
            "  }",
            "",
            "  public Integer bar_migrated(String name) {",
            "    Integer test = 1 + 1;",
            "    return Integer.valueOf(String.valueOf(test) + name);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void twoTypeParameters() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import java.util.function.Function;",
            "public interface Foo {",
            "  public Single<Function<Integer, String>> bar();",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import java.util.function.Function;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "public interface Foo {",
            "  @Deprecated",
            "  default io.reactivex.Single<java.util.function.Function<java.lang.Integer, java.lang.String>> bar() {",
            "     return bar_migrated().as(RxJava2Adapter::monoToSingle);",
            "  }",
            "  default reactor.core.publisher.Mono<java.util.function.Function<java.lang.Integer, java.lang.String>> bar_migrated() {",
            "     return bar().as(RxJava2Adapter::singleToMono);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void positive_singleToMonoInterfaceMigration() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "public interface Foo {",
            "  Single<String> bar();",
            "}")
        .addOutputLines(
            "Foo.java",
            "import io.reactivex.Single;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "public interface Foo {",
            "  @Deprecated",
            "  default io.reactivex.Single<java.lang.String> bar() {",
            "    return bar_migrated().as(RxJava2Adapter::monoToSingle);",
            "  }",
            "  default reactor.core.publisher.Mono<java.lang.String> bar_migrated() {",
            "    return bar().as(RxJava2Adapter::singleToMono);",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void positive_UpdateImplBecauseInterfaceIsUpdated() {
    helper
        .addInputLines(
            "Foo.java",
            "interface Foo {",
            "  @Deprecated",
            "  default java.lang.String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "",
            "  default java.lang.Integer bar_migrated() {",
            "    return Integer.valueOf(bar());",
            "  }",
            "",
            "  Number baz();",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Bar.java",
            "public final class Bar implements Foo {",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "",
            "  public Number baz() {",
            "    return 1;",
            "  }",
            "}")
        .addOutputLines(
            "Bar.java",
            "public final class Bar implements Foo {",
            "  @Deprecated",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"1\");",
            "  }",
            "",
            "  public Number baz() {",
            "    return 1;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void positive_rewriteInterfaceAndNonImplementingClass() {
    helper
        .addInputLines("Foo.java", "interface Foo {", "  String bar();", "  Number baz();", "}")
        .addOutputLines(
            "Foo.java",
            "interface Foo {",
            "  @Deprecated",
            "  default java.lang.String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "",
            "  default java.lang.Integer bar_migrated() {",
            "    return Integer.valueOf(bar());",
            "  }",
            "",
            "  Number baz();",
            "}")
        .addInputLines(
            "Bar.java",
            "public final class Bar {",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "}")
        .addOutputLines(
            "Bar.java",
            "public final class Bar {",
            "  @Deprecated",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"1\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void positive_rewriteInterfaceNotImplementation() {
    helper
        .addInputLines(
            "Bar.java",
            "public final class Bar implements Foo {",
            "  @Override",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "  public Number baz() {",
            "    return 1;",
            "  }",
            "}")
        .expectUnchanged()
        .addInputLines("Foo.java", "interface Foo {", "  String bar();", "  Number baz();", "}")
        .addOutputLines(
            "Foo.java",
            "interface Foo {",
            "  @Deprecated",
            "  default java.lang.String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "",
            "  default java.lang.Integer bar_migrated() {",
            "    return Integer.valueOf(bar());",
            "  }",
            "",
            "  Number baz();",
            "}")
        .doTest();
  }

  @Test
  public void positive_addNewMethodImplementationAndDeprecateOldOne() {
    helper
        .addInputLines(
            "Foo.java",
            "public final class Foo {",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "public final class Foo {",
            "  @Deprecated",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"1\");",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void positive_addNewMethodImplementationForDifficultExpression() {
    helper
        .addInputLines(
            "Foo.java",
            "public final class Foo {",
            "  public String bar() {",
            "    return String.valueOf(\"testValue\");",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "public final class Foo {",
            "  @Deprecated",
            "  public String bar() {",
            "    return String.valueOf(\"testValue\");",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(String.valueOf(\"testValue\"));",
            "  }",
            "}")
        .doTest();
  }

  @Test
  public void positive_StringMigrationWithInterface() {
    helper
        .addInputLines("Foo.java", "interface Foo {", "  String bar();", "  Number baz();", "}")
        .addOutputLines(
            "Foo.java",
            "interface Foo {",
            "  @Deprecated",
            "  default java.lang.String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "",
            "  default java.lang.Integer bar_migrated() {",
            "    return Integer.valueOf(bar());",
            "  }",
            "",
            "  Number baz();",
            "}")
        .doTest();
  }

  @Test
  public void negative_DontMigrateAlreadyMigratedInterface() {
    helper
        .addInputLines(
            "Foo.java",
            "interface Foo {",
            "  @Deprecated",
            "  default String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "",
            "  default Integer bar_migrated() {",
            "    return Integer.valueOf(bar());",
            "  }",
            "",
            "  Number baz();",
            "}")
        .expectUnchanged()
        .doTest();
  }

  @Test
  public void negative_dontMigrateClassWithUndesiredReturnType() {
    helper
        .addInputLines(
            "Foo.java", "public class Foo {", "  Integer bar() {", "    return 1;", "  }", "}")
        .addOutputLines(
            "Foo.java", "public class Foo {", "  Integer bar() {", "    return 1;", "  }", "}")
        .doTest();
  }

  @Test
  public void migrateMaybeNumberToMonoNumber() {
    helper
        .addInputLines(
            "Foo.java",
            "import io.reactivex.Maybe;",
            "public interface Foo {",
            "  Maybe<Integer> bar1();",
            "  Maybe<Number> bar2();",
            "  Maybe<Object> bar3();",
            "}")
        .addOutputLines(
            "out/Foo.java",
            "import io.reactivex.Maybe;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "",
            "public interface Foo {",
            "  @Deprecated  ",
            "  default io.reactivex.Maybe<java.lang.Integer> bar1() {",
            "    return bar1_migrated().as(RxJava2Adapter::monoToMaybe);",
            "  }",
            "",
            "  default reactor.core.publisher.Mono<java.lang.Integer> bar1_migrated() {",
            "    return bar1().as(RxJava2Adapter::maybeToMono);",
            "  }",
            "  @Deprecated  ",
            "  default io.reactivex.Maybe<java.lang.Number> bar2() {",
            "    return bar2_migrated().as(RxJava2Adapter::monoToMaybe);",
            "  }",
            "",
            "  default reactor.core.publisher.Mono<java.lang.Number> bar2_migrated() {",
            "    return bar2().as(RxJava2Adapter::maybeToMono);",
            "  }",
            "",
            "  Maybe<Object> bar3();",
            "}")
        .doTest();
  }

  @Test
  public void deleteOldImplMethodTheMigrationInfoIsAvailable() {
    helper
        .addInputLines(
            "Foo.java",
            "interface Foo {",
            "  @Deprecated",
            "  default java.lang.String bar() {",
            "    return String.valueOf(bar_migrated());",
            "  }",
            "",
            "  default java.lang.Integer bar_migrated() {",
            "    return Integer.valueOf(bar());",
            "  }",
            "",
            "  Number baz();",
            "}")
        .expectUnchanged()
        .addInputLines(
            "Bar.java",
            "public final class Bar implements Foo {",
            "  @Deprecated",
            "  public String bar() {",
            "    return \"1\";",
            "  }",
            "",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"1\");",
            "  }",
            "",
            "  public Number baz() {",
            "    return 1;",
            "  }",
            "}")
        .addOutputLines(
            "out/Bar.java",
            "public final class Bar implements Foo {",
            "  public Integer bar_migrated() {",
            "    return Integer.valueOf(\"1\");",
            "  }",
            "",
            "  public Number baz() {",
            "    return 1;",
            "  }",
            "}")
        .doTest();
  }

  @Test // The `null` here doesn't make sense, but it is about testing the type parameters.
  @Ignore
  public void classWithTypeParameter() {
    helper
        .addInputLines(
            "Test.java",
            "import io.reactivex.Single;",
            "import java.util.function.Function;",
            "public final class Test<T> {",
            "  public Single<Function<Integer, T>> test() {",
            "    return null;",
            "  }",
            "}")
        .addOutputLines(
            "Test.java",
            "import io.reactivex.Single;",
            "import java.util.function.Function;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public final class Test<T> {",
            "  @Deprecated",
            "  public Single<Function<Integer, T>> test() {",
            "     return null;",
            "  }",
            "  public Mono<Function<Integer,T>> test_migrated() {",
            "     return null.as(RxJava2Adapter::singleToMono);",
            "  }",
            "}")
        .doTest();
  }

  @Test // The `null` here doesn't make sense, but it is about testing the type parameters.
  @Ignore
  public void methodWithTypeParameterAndClassWithTypeParameter() {
    helper
        .addInputLines(
            "Test.java",
            "import io.reactivex.Single;",
            "import java.util.function.Function;",
            "public final class Test<T> {",
            "  public <R> Single<Function<R, T>> test() {",
            "    return null;",
            "  }",
            "}")
        .addOutputLines(
            "Test.java",
            "import io.reactivex.Single;",
            "import java.util.function.Function;",
            "import reactor.adapter.rxjava.RxJava2Adapter;",
            "import reactor.core.publisher.Mono;",
            "public final class Test<T> {",
            "  @Deprecated",
            "  public <R> Single<Function<R, T>> test() {",
            "     return null;",
            "  }",
            "  public <R> Mono<Function<R, T>> test_migrated() {",
            "     return null.as(RxJava2Adapter::singleToMono);",
            "  }",
            "}")
        .doTest();
  }
}
