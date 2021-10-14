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

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.bugpatterns.BugChecker.IdentifierTreeMatcher;
import static com.google.errorprone.bugpatterns.BugChecker.MemberSelectTreeMatcher;
import static com.google.errorprone.migration.CountMethods.LibraryType.REACTOR;
import static com.google.errorprone.migration.CountMethods.LibraryType.RX_JAVA;
import static com.google.errorprone.migration.CountMethods.LocationType.CLASS_VARIABLE;
import static com.google.errorprone.migration.CountMethods.LocationType.IMPORT;
import static com.google.errorprone.migration.CountMethods.LocationType.LOCAL_VARIABLE;
import static com.google.errorprone.migration.CountMethods.LocationType.METHOD_INVOCATION;
import static com.google.errorprone.migration.CountMethods.LocationType.METHOD_REFERENCE;
import static com.google.errorprone.migration.CountMethods.LocationType.OTHER;
import static com.google.errorprone.migration.CountMethods.LocationType.PARAM;
import static com.google.errorprone.migration.CountMethods.LocationType.RETURN_TYPE;
import static com.sun.tools.javac.code.Symbol.ClassSymbol;
import static com.sun.tools.javac.code.Symbol.MethodSymbol;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.BugPattern;
import com.google.errorprone.ErrorProneFlags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
    name = "CountMethods",
    summary = "Count the callers of RxJava methods and Reactor methods and types.",
    severity = ERROR)
public class CountMethods extends BugChecker
    implements IdentifierTreeMatcher, MemberSelectTreeMatcher {

  private String directory;

  public CountMethods() {
    this(ErrorProneFlags.empty());
  }

  public CountMethods(ErrorProneFlags flags) {
    this.directory = flags.get("Directory").orElse("dir");

    createDirectoryIfNotExists();
  }

  private void createDirectoryIfNotExists() {
    File directory = new File(this.directory);
    if (!directory.exists()) {
      directory.mkdir();
    }
  }

  public static final ImmutableSet<String> RXJAVA_TYPES =
      ImmutableSet.of(
          "io.reactivex.Observable",
          "io.reactivex.Flowable",
          "io.reactivex.Single",
          "io.reactivex.Maybe",
          "io.reactivex.Completable");

  public static final ImmutableSet<String> REACTOR_TYPES =
      ImmutableSet.of("reactor.core.publisher.Mono", "reactor.core.publisher.Flux");

  @Override
  public Description matchIdentifier(IdentifierTree tree, VisitorState state) {
    Symbol symbol = ASTHelpers.getSymbol(tree);
    if (!(symbol instanceof ClassSymbol)) {
      return Description.NO_MATCH;
    }

    Optional<LibraryType> libraryType = getRxReactorType(symbol);
    if (!libraryType.isPresent()) {
      return Description.NO_MATCH;
    }

    Optional<LocationType> locationType = getExprType(state);
    printLinesForRxJavaAndReactorMethods(tree, libraryType.get(), locationType.get().toString());

    return Description.NO_MATCH;
  }

  @Override
  public Description matchMemberSelect(MemberSelectTree tree, VisitorState state) {
    Symbol symbol = ASTHelpers.getSymbol(tree);
    if (!(symbol instanceof ClassSymbol)) {
      return Description.NO_MATCH;
    }

    Optional<LibraryType> libraryType = getRxReactorType(symbol);
    if (!libraryType.isPresent()) {
      return Description.NO_MATCH;
    }

    Optional<LocationType> locationType = getExprType(state);

    return Description.NO_MATCH;
  }

  private Optional<LibraryType> getRxReactorType(Symbol symbol) {
    if (RXJAVA_TYPES.contains(symbol.toString())) {
      return Optional.of(RX_JAVA);
    } else if (REACTOR_TYPES.contains(symbol.toString())) {
      return Optional.of(REACTOR);
    }
    return Optional.empty();
  }

  private Optional<LocationType> getExprType(VisitorState state) {
    if (ASTHelpers.findPathFromEnclosingNodeToTopLevel(state.getPath(), ImportTree.class) != null) {
      return Optional.of(IMPORT);
    }
    Tree parent = state.getPath().getParentPath().getLeaf();
    Tree grandParent = state.getPath().getParentPath().getParentPath().getLeaf();
    if (grandParent instanceof MethodInvocationTree) {
      return Optional.of(METHOD_INVOCATION);
    } else if (parent instanceof MemberReferenceTree
        || grandParent instanceof MemberReferenceTree) {
      return Optional.of(METHOD_REFERENCE);
    } else if (grandParent instanceof MethodTree) {
      if (parent == ((MethodTree) grandParent).getReturnType()) {
        return Optional.of(RETURN_TYPE);
      }
    } else if (grandParent instanceof VariableTree) {
      TreePath enclosingMethodTreePath =
          ASTHelpers.findPathFromEnclosingNodeToTopLevel(
              state.getPath().getParentPath().getParentPath(), MethodTree.class);
      TreePath enclosingClassTreePath =
          ASTHelpers.findPathFromEnclosingNodeToTopLevel(
              state.getPath().getParentPath().getParentPath(), ClassTree.class);
      boolean isParam = false;
      if (enclosingMethodTreePath != null
          && ASTHelpers.getSymbol(enclosingMethodTreePath.getLeaf()) instanceof MethodSymbol) {
        Symbol grandParentSymbol = ASTHelpers.getSymbol(grandParent);
        isParam =
            ((MethodSymbol) ASTHelpers.getSymbol(enclosingMethodTreePath.getLeaf()))
                .getParameters().stream().anyMatch(p -> p == grandParentSymbol);
      }
      if (isParam) {
        return Optional.of(PARAM);
      }
      if (enclosingMethodTreePath != null) {
        return Optional.of(LOCAL_VARIABLE);
      } else if (enclosingClassTreePath != null) {
        return Optional.of(CLASS_VARIABLE);
      }
    }
    return Optional.of(OTHER);
  }

  private void printLinesForRxJavaAndReactorMethods(
      ExpressionTree tree, LibraryType type, String invocation) {
    MethodSymbol symbol = (MethodSymbol) ASTHelpers.getSymbol(tree);

    String filePrefix = type.equals(RX_JAVA) ? "rx-java" : "reactor";
    String line =
        "1 | "
            + symbol.getSimpleName()
            + " | "
            + symbol.owner.toString()
            + " | "
            + invocation
            + "\n";
    writeString(line, filePrefix);
  }

  private void writeString(String line, String filePrefix) {
    byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
    String fileName = directory + "/" + filePrefix + "-usages.txt";
    FileOutputStream fileOutputStream;
    try {
      fileOutputStream = new FileOutputStream(fileName, true);
      fileOutputStream.write(bytes);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  enum LibraryType {
    RX_JAVA,
    REACTOR
  }

  enum LocationType {
    CLASS_VARIABLE,
    LOCAL_VARIABLE,
    IMPORT,
    RETURN_TYPE,
    METHOD_INVOCATION,
    METHOD_REFERENCE,
    PARAM,
    OTHER
  }
}
