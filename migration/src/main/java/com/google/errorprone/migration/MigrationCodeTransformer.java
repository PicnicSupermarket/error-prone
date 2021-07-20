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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.errorprone.CodeTransformer;
import com.google.errorprone.DescriptionListener;
import com.google.errorprone.refaster.UType;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.util.Context;

import java.io.Serializable;
import java.lang.annotation.Annotation;

@AutoValue
public abstract class MigrationCodeTransformer implements CodeTransformer, Serializable {

  public static MigrationCodeTransformer create(
          CodeTransformer transformFrom, CodeTransformer transformTo, UType typeFrom, UType typeTo) {
    return new AutoValue_MigrationCodeTransformer(transformFrom, transformTo, typeFrom, typeTo);
  }

  MigrationCodeTransformer() {}

  public abstract CodeTransformer transformFrom();

  public abstract CodeTransformer transformTo();

  public abstract UType typeFrom();

  public abstract UType typeTo();

  @Override
  public void apply(TreePath path, Context context, DescriptionListener listener) {
    transformFrom().apply(path, context, listener);
  }

  @Override
  public ImmutableClassToInstanceMap<Annotation> annotations() {
    return ImmutableClassToInstanceMap.of();
  }
}
