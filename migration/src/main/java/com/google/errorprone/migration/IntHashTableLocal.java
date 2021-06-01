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

public class IntHashTableLocal {
  private static final int DEFAULT_INITIAL_SIZE = 64;
  protected Object[] objs;
  protected int[] ints;
  protected int mask;
  protected int num_bindings;
  private static final Object DELETED = new Object();

  public IntHashTableLocal() {
    this.objs = new Object[64];
    this.ints = new int[64];
    this.mask = 63;
  }

  public IntHashTableLocal(int capacity) {
    int log2Size;
    for (log2Size = 4; capacity > 1 << log2Size; ++log2Size) {}

    capacity = 1 << log2Size;
    this.objs = new Object[capacity];
    this.ints = new int[capacity];
    this.mask = capacity - 1;
  }

  public int hash(Object key) {
    return System.identityHashCode(key);
  }

  public int lookup(Object key, int hash) {
    int hash1 = hash ^ hash >>> 15;
    int hash2 = hash ^ hash << 6 | 1;
    int deleted = -1;
    int i = hash1 & this.mask;

    while (true) {
      Object node = this.objs[i];
      if (node == key) {
        return i;
      }

      if (node == null) {
        return deleted >= 0 ? deleted : i;
      }

      if (node == DELETED && deleted < 0) {
        deleted = i;
      }

      i = i + hash2 & this.mask;
    }
  }

  public int lookup(Object key) {
    return this.lookup(key, this.hash(key));
  }

  public int getFromIndex(int index) {
    Object node = this.objs[index];
    return node != null && node != DELETED ? this.ints[index] : -1;
  }

  public int putAtIndex(Object key, int value, int index) {
    Object old = this.objs[index];
    if (old != null && old != DELETED) {
      int oldValue = this.ints[index];
      this.ints[index] = value;
      return oldValue;
    } else {
      this.objs[index] = key;
      this.ints[index] = value;
      if (old != DELETED) {
        ++this.num_bindings;
      }

      if (3 * this.num_bindings >= 2 * this.objs.length) {
        this.rehash();
      }

      return -1;
    }
  }

  public int remove(Object key) {
    int index = this.lookup(key);
    Object old = this.objs[index];
    if (old != null && old != DELETED) {
      this.objs[index] = DELETED;
      return this.ints[index];
    } else {
      return -1;
    }
  }

  protected void rehash() {
    Object[] oldObjsTable = this.objs;
    int[] oldIntsTable = this.ints;
    int oldCapacity = oldObjsTable.length;
    int newCapacity = oldCapacity << 1;
    Object[] newObjTable = new Object[newCapacity];
    int[] newIntTable = new int[newCapacity];
    int newMask = newCapacity - 1;
    this.objs = newObjTable;
    this.ints = newIntTable;
    this.mask = newMask;
    this.num_bindings = 0;
    int i = oldIntsTable.length;

    while (true) {
      --i;
      if (i < 0) {
        return;
      }

      Object key = oldObjsTable[i];
      if (key != null && key != DELETED) {
        this.putAtIndex(key, oldIntsTable[i], this.lookup(key, this.hash(key)));
      }
    }
  }

  public void clear() {
    int i = this.objs.length;

    while (true) {
      --i;
      if (i < 0) {
        this.num_bindings = 0;
        return;
      }

      this.objs[i] = null;
    }
  }
}
