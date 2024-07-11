/*
  * Licensed to the Apache Software Foundation (ASF) under one or more
  * contributor license agreements. See the NOTICE file distributed with
  * this work for additional information regarding copyright ownership.
  * The ASF licenses this file to You under the Apache License, Version 2.0
  * (the "License"); you may not use this file except in compliance with
  * the License. You may obtain a copy of the License at
  *
  *    http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package org.apache.hudi.common.util.collection;

import java.util.Iterator;
import java.util.NoSuchElementException;

/** Iterator flattening source {@link Iterator} holding other {@link Iterator}s */
public final class FlatteningIterator<T, I extends Iterator<T>> implements Iterator<T> {

    private final Iterator<I> sourceIterator;
    private Iterator<T> innerSourceIterator;

    public FlatteningIterator(Iterator<I> source) {
        this.sourceIterator = source;
    }

    public boolean hasNext() {
        while (innerSourceIterator == null || !innerSourceIterator.hasNext()) {
            if (sourceIterator.hasNext()) {
                innerSourceIterator = sourceIterator.next();
            } else {
                return false;
            }
        }

        return true;
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        return innerSourceIterator.next();
    }
}
