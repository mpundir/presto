/*
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
package com.facebook.presto;

import com.facebook.presto.spi.Page;
import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.block.BlockBuilderStatus;
import com.facebook.presto.spi.block.VariableWidthBlockBuilder;
import com.facebook.presto.spi.type.StandardTypes;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.type.ArrayType;
import com.google.common.collect.ImmutableList;
import io.airlift.slice.Slice;
import io.airlift.slice.Slices;

import java.util.List;
import java.util.Map;

import static com.facebook.presto.type.TypeJsonUtils.appendToBlockBuilder;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class RowPageBuilder
{
    private final List<Type> types;

    public static RowPageBuilder rowPageBuilder(Type... types)
    {
        return rowPageBuilder(ImmutableList.copyOf(types));
    }

    public static RowPageBuilder rowPageBuilder(Iterable<Type> types)
    {
        return new RowPageBuilder(types);
    }

    private final List<BlockBuilder> builders;
    private long rowCount;

    RowPageBuilder(Iterable<Type> types)
    {
        this.types = ImmutableList.copyOf(requireNonNull(types, "types is null"));
        ImmutableList.Builder<BlockBuilder> builders = ImmutableList.builder();
        for (Type type : types) {
            builders.add(type.createBlockBuilder(new BlockBuilderStatus(), 1));
        }
        this.builders = builders.build();
        checkArgument(!this.builders.isEmpty(), "At least one value info is required");
    }

    public boolean isEmpty()
    {
        return rowCount == 0;
    }

    public RowPageBuilder row(Object... values)
    {
        checkArgument(values.length == builders.size(), "Expected %s values, but got %s", builders.size(), values.length);

        for (int channel = 0; channel < values.length; channel++) {
            append(channel, values[channel]);
        }
        rowCount++;
        return this;
    }

    public Page build()
    {
        Block[] blocks = new Block[builders.size()];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = builders.get(i).build();
        }
        return new Page(blocks);
    }

    private void append(int channel, Object value)
    {
        BlockBuilder builder = builders.get(channel);

        Type type = types.get(channel);
        Class<?> javaType = type.getJavaType();
        if (value == null) {
            builder.appendNull();
        }
        else if (type.getTypeSignature().getBase().equals(StandardTypes.ARRAY) && value instanceof Iterable<?>) {
            BlockBuilder subBlockBuilder = ((ArrayType) type).getElementType().createBlockBuilder(new BlockBuilderStatus(), 100);
            for (Object subElement : (Iterable<?>) value) {
                appendToBlockBuilder(type.getTypeParameters().get(0), subElement, subBlockBuilder);
            }
            type.writeObject(builder, subBlockBuilder);
        }
        else if (type.getTypeSignature().getBase().equals(StandardTypes.ROW) && value instanceof Iterable<?>) {
            BlockBuilder subBlockBuilder = new VariableWidthBlockBuilder(new BlockBuilderStatus());
            int field = 0;
            for (Object subElement : (Iterable<?>) value) {
                appendToBlockBuilder(type.getTypeParameters().get(field), subElement, subBlockBuilder);
                field++;
            }
            type.writeObject(builder, subBlockBuilder);
        }
        else if (type.getTypeSignature().getBase().equals(StandardTypes.MAP) && value instanceof Map<?, ?>) {
            BlockBuilder subBlockBuilder = new VariableWidthBlockBuilder(new BlockBuilderStatus());
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                appendToBlockBuilder(type.getTypeParameters().get(0), entry.getKey(), subBlockBuilder);
                appendToBlockBuilder(type.getTypeParameters().get(1), entry.getValue(), subBlockBuilder);
            }
            type.writeObject(builder, subBlockBuilder);
        }
        else if (javaType == boolean.class) {
            type.writeBoolean(builder, (Boolean) value);
        }
        else if (javaType == long.class) {
            type.writeLong(builder, ((Number) value).longValue());
        }
        else if (javaType == double.class) {
            type.writeDouble(builder, (Double) value);
        }
        else if (javaType == Slice.class) {
            Slice slice = value instanceof Slice ? (Slice) value : Slices.utf8Slice((String) value);
            type.writeSlice(builder, slice, 0, slice.length());
        }
        else {
            type.writeObject(builder, value);
        }
    }
}
