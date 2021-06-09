/*
 * Copyright 2013 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.refaster;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.VisitorState;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import javax.annotation.Nullable;
import java.util.List;

import static com.google.errorprone.refaster.Unifier.unifications;
import static com.sun.tools.javac.code.Flags.STATIC;

@AutoValue
public abstract class CType extends Types.SimpleVisitor<Choice<Unifier>, Unifier>
    implements Unifiable<Type> {

  public static CType create(String fullyQualifiedClass) {
    return new AutoValue_CType(fullyQualifiedClass);
  }

  abstract String fullyQualifiedClass();

  @Override
  public Choice<Unifier> visitClassType(ClassType classType, Unifier unifier) {
    return unify(classType, unifier);
  }

  @Override
  @Nullable
  public Choice<Unifier> visitType(Type t, @Nullable Unifier unifier) {
    return Choice.none();
  }

  @Override
  public Choice<Unifier> unify(Type target, Unifier unifier) {
    VisitorState visitorState = new VisitorState(unifier.getContext());
    Type typeFromString = visitorState.getTypeFromString(fullyQualifiedClass());
    return Choice.condition(unifier.types().isConvertible(target, typeFromString), unifier);
  }
}
