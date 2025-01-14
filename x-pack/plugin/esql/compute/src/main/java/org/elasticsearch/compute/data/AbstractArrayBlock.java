/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.data;

import org.elasticsearch.core.Nullable;

import java.util.BitSet;

abstract class AbstractArrayBlock extends AbstractNonThreadSafeRefCounted implements Block {
    private final MvOrdering mvOrdering;
    protected final int positionCount;

    @Nullable
    protected final int[] firstValueIndexes;

    @Nullable
    protected final BitSet nullsMask;

    /**
     * @param positionCount the number of values in this block
     */
    protected AbstractArrayBlock(int positionCount, @Nullable int[] firstValueIndexes, @Nullable BitSet nullsMask, MvOrdering mvOrdering) {
        this.positionCount = positionCount;
        this.firstValueIndexes = firstValueIndexes;
        this.mvOrdering = mvOrdering;
        this.nullsMask = nullsMask == null || nullsMask.isEmpty() ? null : nullsMask;
        assert nullsMask != null || firstValueIndexes != null : "Create VectorBlock instead";
        assert assertInvariants();
    }

    @Override
    public final boolean mayHaveMultivaluedFields() {
        /*
         * This could return a false positive if all the indices are one away from
         * each other. But we will try to avoid that.
         */
        return firstValueIndexes != null;
    }

    @Override
    public final MvOrdering mvOrdering() {
        return mvOrdering;
    }

    protected final BitSet shiftNullsToExpandedPositions() {
        BitSet expanded = new BitSet(nullsMask.size());
        int next = -1;
        while ((next = nullsMask.nextSetBit(next + 1)) != -1) {
            expanded.set(getFirstValueIndex(next));
        }
        return expanded;
    }

    private boolean assertInvariants() {
        if (firstValueIndexes != null) {
            assert firstValueIndexes.length == getPositionCount() + 1;
            for (int i = 0; i < getPositionCount(); i++) {
                assert (firstValueIndexes[i + 1] - firstValueIndexes[i]) >= 0;
            }
        }
        if (nullsMask != null) {
            assert nullsMask.nextSetBit(getPositionCount() + 1) == -1;
        }
        if (firstValueIndexes != null && nullsMask != null) {
            for (int i = 0; i < getPositionCount(); i++) {
                // Either we have multi-values or a null but never both.
                assert ((nullsMask.get(i) == false) || (firstValueIndexes[i + 1] - firstValueIndexes[i]) == 1);
            }
        }
        return true;
    }

    @Override
    public final int getTotalValueCount() {
        if (firstValueIndexes == null) {
            return positionCount - nullValuesCount();
        }
        return firstValueIndexes[positionCount] - nullValuesCount();
    }

    @Override
    public final int getPositionCount() {
        return positionCount;
    }

    /** Gets the index of the first value for the given position. */
    public final int getFirstValueIndex(int position) {
        return firstValueIndexes == null ? position : firstValueIndexes[position];
    }

    /** Gets the number of values for the given position, possibly 0. */
    @Override
    public final int getValueCount(int position) {
        return isNull(position) ? 0 : firstValueIndexes == null ? 1 : firstValueIndexes[position + 1] - firstValueIndexes[position];
    }

    @Override
    public final boolean isNull(int position) {
        return mayHaveNulls() && nullsMask.get(position);
    }

    @Override
    public final boolean mayHaveNulls() {
        return nullsMask != null;
    }

    @Override
    public final int nullValuesCount() {
        return mayHaveNulls() ? nullsMask.cardinality() : 0;
    }

    @Override
    public final boolean areAllValuesNull() {
        return nullValuesCount() == getPositionCount();
    }
}
