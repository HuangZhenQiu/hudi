/*
  * Licensed to the Apache Software Foundation (ASF) under one
  * or more contributor license agreements.  See the NOTICE file
  * distributed with this work for additional information
  * regarding copyright ownership.  The ASF licenses this file
  * to you under the Apache License, Version 2.0 (the
  * "License"); you may not use this file except in compliance
  * with the License.  You may obtain a copy of the License at
  *
  *      http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package org.apache.hudi.internal.schema.visitor;

import static org.apache.hudi.internal.schema.utils.InternalSchemaUtils.createFullName;

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hudi.internal.schema.InternalSchema;
import org.apache.hudi.internal.schema.Type;
import org.apache.hudi.internal.schema.Types;

/**
  * Schema visitor to produce name -> position map for internalSchema. Positions are assigned in a
  * depth-first manner.
  */
public class NameToPositionVisitor extends InternalSchemaVisitor<Map<String, Integer>> {
    private final Deque<String> fieldNames = new LinkedList<>();
    private final Map<String, Integer> nameToId = new HashMap<>();
    private final AtomicInteger position = new AtomicInteger(0);

    @Override
    public void beforeField(Types.Field field) {
        nameToId.put(createFullName(field.name(), fieldNames), position.getAndIncrement());
        fieldNames.push(field.name());
    }

    @Override
    public void afterField(Types.Field field) {
        fieldNames.pop();
    }

    @Override
    public void beforeArrayElement(Types.Field elementField) {
        nameToId.put(
                createFullName(InternalSchema.ARRAY_ELEMENT, fieldNames), position.getAndIncrement());
        fieldNames.push(elementField.name());
    }

    @Override
    public void afterArrayElement(Types.Field elementField) {
        fieldNames.pop();
    }

    @Override
    public void beforeMapKey(Types.Field keyField) {
        nameToId.put(createFullName(InternalSchema.MAP_KEY, fieldNames), position.getAndIncrement());
        fieldNames.push(keyField.name());
    }

    @Override
    public void afterMapKey(Types.Field keyField) {
        fieldNames.pop();
    }

    @Override
    public void beforeMapValue(Types.Field valueField) {
        nameToId.put(createFullName(InternalSchema.MAP_VALUE, fieldNames), position.getAndIncrement());
        fieldNames.push(valueField.name());
    }

    @Override
    public void afterMapValue(Types.Field valueField) {
        fieldNames.pop();
    }

    @Override
    public Map<String, Integer> schema(InternalSchema schema, Map<String, Integer> recordResult) {
        return nameToId;
    }

    @Override
    public Map<String, Integer> record(
            Types.RecordType record, List<Map<String, Integer>> fieldResults) {
        return nameToId;
    }

    @Override
    public Map<String, Integer> field(Types.Field field, Map<String, Integer> fieldResult) {
        return nameToId;
    }

    @Override
    public Map<String, Integer> array(Types.ArrayType array, Map<String, Integer> elementResult) {
        return nameToId;
    }

    @Override
    public Map<String, Integer> map(
            Types.MapType map, Map<String, Integer> keyResult, Map<String, Integer> valueResult) {
        return nameToId;
    }

    @Override
    public Map<String, Integer> primitive(Type.PrimitiveType primitive) {
        return nameToId;
    }
}
