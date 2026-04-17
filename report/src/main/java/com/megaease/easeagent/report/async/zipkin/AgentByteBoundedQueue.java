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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;

/**
 * Multi-producer, multi-consumer queue that is bounded by both count and size.
 *
 * 收到大小和数量限制的多生产者、多消费者队列。
 *
 * <p>
 * This queue is implemented based on LinkedTransferQueue and implements the maximum number and the maximum number of bytes
 * on the basis of LinkedTransferQueue. Taking advantage of the lock-free performance of LinkedTransferQueue in
 * inserting data, the performance problem of locking in the old version can be avoided.
 * </p>
 *
 * 该队列基于 LinkedTransferQueue 实现，并在其基础上实现了最大数量和最大字节数的限制。
 * 基于 LinkedTransferQueue 是为了利用其插入数据过程中的无锁特性，相比旧版本中插入数据
 * 存在的锁的性能提升明显。
 */
public final class AgentByteBoundedQueue<S> implements WithSizeConsumer<S> {

    private final LinkedTransferQueue<DataWrapper<S>> queue = new LinkedTransferQueue<>();

    // 当前队列中的累计字节大小
    private final AtomicInteger sizeInBytes = new AtomicInteger(0);

    // 最大数量限制，其比较对象为 queue#size
    private final int maxSize;

    // 最大字节数限制，其比较对象为 sizeInBytes
    private final int maxBytes;

    // 丢弃数据的计数器
    private final LongAdder loseCounter = new LongAdder();

    public AgentByteBoundedQueue(int maxSize, int maxBytes) {
        this.maxSize = maxSize;
        this.maxBytes = maxBytes;
    }

    /**
     * 返回 true 代表元素可以被插入；否则元素不可被插入
     *
     * @param next
     * @param nextSizeInBytes
     * @return
     */
    @Override
    public boolean offer(S next, int nextSizeInBytes) {
        // 检查元素数量是否到达上限
        if (maxSize == queue.size()) { // O(n)
            // 丢弃的记录+1
            loseCounter.increment();
            return false;
        }
        // 检查字节数量是否到达上限
        if (sizeInBytes.updateAndGet(pre -> pre + nextSizeInBytes) > maxBytes) { // 处理超过限制的场景
            // 丢弃的记录+1
            loseCounter.increment();
            // 将增加的数量减少
            sizeInBytes.updateAndGet(pre -> pre - nextSizeInBytes);
            return false;
        }
        // 将数据塞入队列
        queue.offer(new DataWrapper<>(next, nextSizeInBytes));
        return true;
    }

    // 持续将当前 queue 中的元素移动到 consumer 中
    int doDrain(WithSizeConsumer<S> consumer, DataWrapper<S> firstPoll) {
        int drainedCount = 0;
        int drainedSizeInBytes = 0;
        // queue 中的首个元素
        DataWrapper<S> next = firstPoll;
        do {
            // 读取元素的字节大小
            int nextSizeInBytes = next.getSizeInBytes();
            // 检查元素能否放入 consumer
            if (consumer.offer(next.getElement(), nextSizeInBytes)) { // 处理可以放入的场景
                // 插入数量计数+1
                drainedCount++;
                // 插入字节数统计
                drainedSizeInBytes += nextSizeInBytes;
            } else { // 处理不能放入的场景
                // 转而放入当前 queue
                queue.offer(next);
                break;
            }
        } while ((next = queue.poll()) != null);
        final int updateValue = drainedSizeInBytes;
        // 更新移动的元素的字节数
        sizeInBytes.updateAndGet(pre -> pre - updateValue);
        return drainedCount;
    }

    // 持续将当前 queue 中的元素移动到 consumer 中，并返回移动的元素数量
    public int drainTo(WithSizeConsumer<S> consumer, long nanosTimeout) {
        DataWrapper<S> firstPoll;
        try {
            // 读取头部的元素，等待指定时间
            firstPoll = queue.poll(nanosTimeout, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            return 0;
        }
        // 如果当前 queue 中没有元素，那么就不执行后续操作
        if (firstPoll == null) {
            return 0;
        }

        return doDrain(consumer, firstPoll);
    }

    public int getCount() {
        return queue.size();
    }

    public int getSizeInBytes() {
        return sizeInBytes.get();
    }

    /**
     * 清除数据，返回移除的数据数量
     *
     * @return
     */
    public int clear() {
        DataWrapper<S> data;
        int result = 0;
        int removeBytes = 0;
        while ((data = queue.poll()) != null) {
            // 统计移除的元素的大小
            removeBytes += data.getSizeInBytes();
            // 统计移除的元素的数量
            result++;
        }
        // 更新字节大小统计
        sizeInBytes.addAndGet(removeBytes * -1);
        return result;
    }

    public long getLoseCount() {
        return loseCounter.longValue();
    }

    @Data
    private static class DataWrapper<S> {
        // 元素
        private final S element;
        // 元素大小+JSON格式的字节[,]后的大小
        private final int sizeInBytes;
    }

}

