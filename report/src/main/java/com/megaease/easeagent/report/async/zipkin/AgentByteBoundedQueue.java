package com.megaease.easeagent.report.async.zipkin;

/*
 * Copyright 2016-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import lombok.Data;

import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-producer, multi-consumer queue that is bounded by both count and size.
 *
 * <p>This is similar to {@link java.util.concurrent.ArrayBlockingQueue} in implementation.
 */
public final class AgentByteBoundedQueue<S> implements WithSizeConsumer<S> {
    private final LinkedTransferQueue<DataWrapper<S>> queue = new LinkedTransferQueue<>();

    private final int maxSize;

    private final int maxBytes;

    private final AtomicLong sizeInBytes = new AtomicLong(0L);

    public AgentByteBoundedQueue(int maxSize, int maxBytes) {
        this.maxSize = maxSize;
        this.maxBytes = maxBytes;
    }

    @Override
    public boolean offer(S next, int nextSizeInBytes) {
        if (maxSize == queue.size()) {
            return false;
        }
        if (sizeInBytes.updateAndGet(pre -> pre + nextSizeInBytes) > maxBytes) {
            return false;
        }
        queue.offer(new DataWrapper<>(next, nextSizeInBytes));
        return true;
    }

    int doDrain(WithSizeConsumer<S> consumer, DataWrapper<S> firstPoll) {
        int drainedCount = 0;
        int drainedSizeInBytes = 0;
        DataWrapper<S> next = firstPoll;
        do {
            int nextSizeInBytes = next.getSizeInBytes();
            if (consumer.offer(next.getElement(), nextSizeInBytes)) {
                drainedCount++;
                drainedSizeInBytes += nextSizeInBytes;
            } else {
                break;
            }
            next = queue.poll();
            if (next == null) break;
        } while (drainedCount < queue.size());
        final int updateValue = drainedSizeInBytes;
        sizeInBytes.updateAndGet(pre -> pre - updateValue);
        return drainedCount;
    }

    public int drainTo(WithSizeConsumer<S> consumer, long nanosTimeout) {
        DataWrapper<S> firstPoll;
        try {
            firstPoll = queue.poll(nanosTimeout, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            return 0;
        }
        if (firstPoll == null) {
            return 0;
        }
        return doDrain(consumer, firstPoll);
    }

    public int getCount() {
        return queue.size();
    }

    public int getSizeInBytes() {
        return sizeInBytes.intValue();
    }

    public int clear() {
        int result = queue.size();
        queue.clear();
        sizeInBytes.set(0L);
        return result;
    }

    @Data
    private static class DataWrapper<S> {

        private final S element;

        private final int sizeInBytes;
    }
}

