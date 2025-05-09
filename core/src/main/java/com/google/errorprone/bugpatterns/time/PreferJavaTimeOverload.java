/*
 * Copyright 2019 The Error Prone Authors.
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
package com.google.errorprone.bugpatterns.time;

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.Matchers.constructor;
import static com.google.errorprone.matchers.method.MethodMatchers.instanceMethod;
import static com.google.errorprone.matchers.method.MethodMatchers.staticMethod;
import static com.google.errorprone.util.ASTHelpers.getStartPosition;
import static com.google.errorprone.util.ASTHelpers.getSymbol;
import static com.google.errorprone.util.ASTHelpers.hasOverloadWithOnlyOneParameter;
import static com.google.errorprone.util.ASTHelpers.isSameType;
import static com.google.errorprone.util.ASTHelpers.isSubtype;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** This check suggests the use of {@code java.time}-based APIs, when available. */
@BugPattern(
    altNames = {"PreferDurationOverload"},
    summary =
        "Prefer using java.time-based APIs when available. Note that this checker does"
            + " not and cannot guarantee that the overloads have equivalent semantics, but that is"
            + " generally the case with overloaded methods.",
    severity = WARNING)
public final class PreferJavaTimeOverload extends BugChecker
    implements MethodInvocationTreeMatcher {

  private static final String JAVA_DURATION = "java.time.Duration";
  private static final String JODA_DURATION = "org.joda.time.Duration";

  private static final ImmutableMap<Matcher<ExpressionTree>, TimeUnit>
      JODA_DURATION_FACTORY_MATCHERS =
          new ImmutableMap.Builder<Matcher<ExpressionTree>, TimeUnit>()
              .put(constructor().forClass(JODA_DURATION).withParameters("long"), MILLISECONDS)
              .put(staticMethod().onClass(JODA_DURATION).named("millis"), MILLISECONDS)
              .put(staticMethod().onClass(JODA_DURATION).named("standardSeconds"), SECONDS)
              .put(staticMethod().onClass(JODA_DURATION).named("standardMinutes"), MINUTES)
              .put(staticMethod().onClass(JODA_DURATION).named("standardHours"), HOURS)
              .put(staticMethod().onClass(JODA_DURATION).named("standardDays"), DAYS)
              .buildOrThrow();

  private static final ImmutableMap<TimeUnit, String> TIMEUNIT_TO_DURATION_FACTORY =
      new ImmutableMap.Builder<TimeUnit, String>()
          .put(NANOSECONDS, "%s.ofNanos(%s)")
          .put(MICROSECONDS, "%s.of(%s, %s)")
          .put(MILLISECONDS, "%s.ofMillis(%s)")
          .put(SECONDS, "%s.ofSeconds(%s)")
          .put(MINUTES, "%s.ofMinutes(%s)")
          .put(HOURS, "%s.ofHours(%s)")
          .put(DAYS, "%s.ofDays(%s)")
          .buildOrThrow();

  private static final String JAVA_INSTANT = "java.time.Instant";
  private static final String JODA_INSTANT = "org.joda.time.Instant";

  private static final Matcher<ExpressionTree> JODA_INSTANT_CONSTRUCTOR_MATCHER =
      constructor().forClass(JODA_INSTANT).withParameters("long");

  private static final String TIME_SOURCE = "com.google.common.time.TimeSource";
  private static final String JODA_CLOCK = "com.google.common.time.Clock";

  private static final String JAVA_TIME_CONVERSIONS =
      "com.google.thirdparty.jodatime.JavaTimeConversions";

  private static final Matcher<ExpressionTree> TO_JODA_DURATION =
      staticMethod().onClass(JAVA_TIME_CONVERSIONS).named("toJodaDuration");
  private static final Matcher<ExpressionTree> TO_JODA_INSTANT =
      staticMethod().onClass(JAVA_TIME_CONVERSIONS).named("toJodaInstant");

  private static final Matcher<ExpressionTree> IGNORED_APIS =
      anyOf(
          staticMethod().onClass("org.jooq.impl.DSL").withAnyName(),
          // any static method under org.assertj.*
          staticMethod()
              .onClass((type, state) -> type.toString().startsWith("org.assertj."))
              .withAnyName(),
          // any instance method on Reactor's Flux API
          instanceMethod().onDescendantOf("reactor.core.publisher.Flux").withAnyName());

  private static final Matcher<ExpressionTree> JAVA_DURATION_DECOMPOSITION_MATCHER =
      instanceMethod()
          .onExactClass(JAVA_DURATION)
          .namedAnyOf(
              "toNanos", "toMillis", "toSeconds", "toMinutes", "toHours", "toDays", "getSeconds");

  // TODO(kak): Add support for constructors that accept a <long, TimeUnit> or JodaTime Duration

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {

    // we return no match for a set of explicitly ignored APIs
    if (IGNORED_APIS.matches(tree, state)) {
      return Description.NO_MATCH;
    }

    List<? extends ExpressionTree> arguments = tree.getArguments();

    // TODO(glorioso): Add support for methods with > 2 parameters. E.g.,
    // foo(String, long, TimeUnit, Frobber) -> foo(String, Duration, Frobber)

    if (isNumericMethodCall(tree, state)) {
      if (hasJavaTimeOverload(tree, JAVA_DURATION_TYPE.get(state), state)) {
        return buildDescriptionForNumericPrimitive(tree, state, arguments, "Duration");
      }
      if (hasJavaTimeOverload(tree, JAVA_INSTANT_TYPE.get(state), state)) {
        return buildDescriptionForNumericPrimitive(tree, state, arguments, "Instant");
      }
    }

    if (isLongTimeUnitMethodCall(tree, state)) {
      Optional<TimeUnit> optionalTimeUnit = DurationToLongTimeUnit.getTimeUnit(arguments.get(1));
      if (optionalTimeUnit.isPresent()) {
        if (hasJavaTimeOverload(tree, JAVA_DURATION_TYPE.get(state), state)) {
          String durationFactory = TIMEUNIT_TO_DURATION_FACTORY.get(optionalTimeUnit.get());
          if (durationFactory != null) {
            SuggestedFix.Builder fix = SuggestedFix.builder();
            String qualifiedDuration = SuggestedFixes.qualifyType(state, fix, JAVA_DURATION);
            String value = state.getSourceForNode(arguments.get(0));
            String replacement = null;

            // rewrite foo(javaDuration.getSeconds(), SECONDS) -> foo(javaDuration)
            if (arguments.get(0) instanceof MethodInvocationTree maybeDurationDecomposition) {
              if (JAVA_DURATION_DECOMPOSITION_MATCHER.matches(maybeDurationDecomposition, state)) {
                if (isSameType(
                    ASTHelpers.getReceiverType(maybeDurationDecomposition),
                    JAVA_DURATION_TYPE.get(state),
                    state)) {
                  replacement =
                      state.getSourceForNode(ASTHelpers.getReceiver(maybeDurationDecomposition));
                }
              }
            }

            // handle microseconds separately, since there is no Duration factory for micros
            if (optionalTimeUnit.get() == MICROSECONDS) {
              String qualifiedChronoUnit =
                  SuggestedFixes.qualifyType(state, fix, "java.time.temporal.ChronoUnit");
              replacement =
                  String.format(
                      durationFactory, qualifiedDuration, value, qualifiedChronoUnit + ".MICROS");
            }

            // Otherwise, just use the normal replacement
            if (replacement == null) {
              replacement = String.format(durationFactory, qualifiedDuration, value);
            }

            fix.replace(
                getStartPosition(arguments.get(0)),
                state.getEndPosition(arguments.get(1)),
                replacement);
            return describeMatch(tree, fix.build());
          }
        }
      }
    }

    if (isMethodCallWithSingleParameter(tree, state, "org.joda.time.ReadableDuration")) {
      ExpressionTree arg0 = arguments.get(0);
      if (hasJavaTimeOverload(tree, JAVA_DURATION_TYPE.get(state), state)) {
        SuggestedFix.Builder fix = SuggestedFix.builder();
        // TODO(kak): Maybe only emit a match if Duration doesn't have to be fully qualified?
        String qualifiedDuration = SuggestedFixes.qualifyType(state, fix, JAVA_DURATION);

        // TODO(kak): Add support for org.joda.time.Duration.ZERO -> java.time.Duration.ZERO

        // If the Joda Duration is being constructed inline, then unwrap it.
        for (Map.Entry<Matcher<ExpressionTree>, TimeUnit> entry :
            JODA_DURATION_FACTORY_MATCHERS.entrySet()) {
          if (entry.getKey().matches(arg0, state)) {
            String value = null;
            if (arg0 instanceof MethodInvocationTree jodaDurationCreation) {
              value = state.getSourceForNode(jodaDurationCreation.getArguments().get(0));
            }
            if (arg0 instanceof NewClassTree jodaDurationCreation) {
              value = state.getSourceForNode(jodaDurationCreation.getArguments().get(0));
            }

            if (value != null) {
              String durationFactory = TIMEUNIT_TO_DURATION_FACTORY.get(entry.getValue());
              if (durationFactory != null) {
                String replacement = String.format(durationFactory, qualifiedDuration, value);

                fix.replace(arg0, replacement);
                return describeMatch(tree, fix.build());
              }
            }
          }
        }

        // If we're converting to a JodaTime Duration (from a java.time Duration) to call the
        // JodaTime overload, just unwrap it!
        if (TO_JODA_DURATION.matches(arg0, state)) {
          fix.replace(
              arg0, state.getSourceForNode(((MethodInvocationTree) arg0).getArguments().get(0)));
          return describeMatch(tree, fix.build());
        }

        fix.replace(
            arg0,
            String.format(
                "%s.ofMillis(%s.getMillis())", qualifiedDuration, state.getSourceForNode(arg0)));
        return describeMatch(tree, fix.build());
      }
    }

    if (isMethodCallWithSingleParameter(tree, state, "org.joda.time.ReadableInstant")) {
      ExpressionTree arg0 = arguments.get(0);
      if (hasJavaTimeOverload(tree, JAVA_INSTANT_TYPE.get(state), state)) {
        SuggestedFix.Builder fix = SuggestedFix.builder();
        // TODO(kak): Maybe only emit a match if Instant doesn't have to be fully qualified?
        String qualifiedInstant = SuggestedFixes.qualifyType(state, fix, JAVA_INSTANT);

        // TODO(kak): Add support for org.joda.time.Instant.EPOCH -> java.time.Instant.EPOCH

        // If the Joda Instant is being constructed inline, then unwrap it.
        if (JODA_INSTANT_CONSTRUCTOR_MATCHER.matches(arg0, state)) {
          if (arg0 instanceof NewClassTree jodaInstantCreation) {
            String value = state.getSourceForNode(jodaInstantCreation.getArguments().get(0));
            fix.replace(arg0, String.format("%s.ofEpochMilli(%s)", qualifiedInstant, value));
            return describeMatch(tree, fix.build());
          }
        }

        // If we're converting to a JodaTime Instant (from a java.time Instant) to call the JodaTime
        // overload, just unwrap it!
        if (TO_JODA_INSTANT.matches(arg0, state)) {
          fix.replace(
              arg0, state.getSourceForNode(((MethodInvocationTree) arg0).getArguments().get(0)));
          return describeMatch(tree, fix.build());
        }

        fix.replace(
            arg0,
            String.format(
                "%s.ofEpochMilli(%s.getMillis())", qualifiedInstant, state.getSourceForNode(arg0)));
        return describeMatch(tree, fix.build());
      }
    }

    return Description.NO_MATCH;
  }

  private Description buildDescriptionForNumericPrimitive(
      MethodInvocationTree tree,
      VisitorState state,
      List<? extends ExpressionTree> arguments,
      String javaTimeType) {
    // we don't know what units to use, but we can still warn the user!
    return buildDescription(tree)
        .setMessage(
            String.format(
                "If the numeric primitive (%s) represents a %s, please call %s(%s) instead.",
                state.getSourceForNode(arguments.get(0)),
                javaTimeType,
                state.getSourceForNode(tree.getMethodSelect()),
                javaTimeType))
        .build();
  }

  private static boolean isNumericMethodCall(MethodInvocationTree tree, VisitorState state) {
    List<VarSymbol> params = getSymbol(tree).getParameters();
    if (params.size() == 1) {
      Type type0 = params.get(0).asType();
      return isSameType(type0, state.getSymtab().intType, state)
          || isSameType(type0, state.getSymtab().longType, state)
          || isSameType(type0, state.getSymtab().doubleType, state);
    }
    return false;
  }

  private static boolean isMethodCallWithSingleParameter(
      MethodInvocationTree tree, VisitorState state, String typeName) {
    Type type = state.getTypeFromString(typeName);
    List<VarSymbol> params = getSymbol(tree).getParameters();
    return (params.size() == 1) && isSubtype(params.get(0).asType(), type, state);
  }

  private static boolean isLongTimeUnitMethodCall(MethodInvocationTree tree, VisitorState state) {
    Type longType = state.getSymtab().longType;
    Type timeUnitType = TIME_UNIT_TYPE.get(state);
    List<VarSymbol> params = getSymbol(tree).getParameters();
    if (params.size() == 2) {
      return isSameType(params.get(0).asType(), longType, state)
          && isSameType(params.get(1).asType(), timeUnitType, state);
    }
    return false;
  }

  private static boolean hasJavaTimeOverload(
      MethodInvocationTree tree, Type type, VisitorState state) {
    MethodSymbol calledMethod = getSymbol(tree);
    return hasOverloadWithOnlyOneParameter(calledMethod, calledMethod.name, type, state);
  }

  private static boolean hasTimeSourceMethod(MethodInvocationTree tree, VisitorState state) {
    MethodSymbol calledMethod = getSymbol(tree);
    String timeSourceBasedName = calledMethod.name.toString().replace("Clock", "TimeSource");
    return hasOverloadWithOnlyOneParameter(
        calledMethod, state.getName(timeSourceBasedName), TIME_SOURCE_TYPE.get(state), state);
  }

  private static final Supplier<Type> JAVA_DURATION_TYPE =
      VisitorState.memoize(state -> state.getTypeFromString(JAVA_DURATION));

  private static final Supplier<Type> JAVA_INSTANT_TYPE =
      VisitorState.memoize(state -> state.getTypeFromString(JAVA_INSTANT));

  private static final Supplier<Type> TIME_UNIT_TYPE =
      VisitorState.memoize(state -> state.getTypeFromString("java.util.concurrent.TimeUnit"));

  private static final Supplier<Type> TIME_SOURCE_TYPE =
      VisitorState.memoize(state -> state.getTypeFromString(TIME_SOURCE));
}
