package org.mwg.core.chunk;

import org.junit.Assert;
import org.junit.Test;
import org.mwg.Type;
import org.mwg.core.Constants;
import org.mwg.Graph;
import org.mwg.struct.*;
import org.mwg.core.chunk.heap.HeapStateChunk;
import org.mwg.core.chunk.heap.HeapChunk;
import org.mwg.core.chunk.offheap.*;
import org.mwg.core.utility.PrimitiveHelper;
import org.mwg.core.utility.Unsafe;

public class StateChunkTest implements ChunkListener {

    private int nbCount = 0;

    public interface StateChunkFactory {
        StateChunk create(ChunkListener listener, Buffer payload, Chunk origin);
    }

    @Test
    public void heapStateChunkTest() {
        StateChunkFactory factory = new StateChunkFactory() {

            @Override
            public StateChunk create(ChunkListener listener, Buffer payload, Chunk origin) {
                return new HeapStateChunk(Constants.NULL_LONG, Constants.NULL_LONG, Constants.NULL_LONG, listener, payload, origin);
            }
        };

        saveLoadTest(factory);
        protectionTest(factory);
        typeSwitchTest(factory);
        cloneTest(factory);
    }


    @Test
    public void offHeapStateChunkTest() {
        StateChunkFactory factory = new StateChunkFactory() {

            @Override
            public StateChunk create(ChunkListener listener, Buffer payload, Chunk origin) {
                return new OffHeapStateChunk(listener, Constants.OFFHEAP_NULL_PTR, payload, origin);
            }
        };

        OffHeapByteArray.alloc_counter = 0;
        OffHeapDoubleArray.alloc_counter = 0;
        OffHeapLongArray.alloc_counter = 0;
        OffHeapStringArray.alloc_counter = 0;

        Unsafe.DEBUG_MODE = true;

        saveLoadTest(factory);
        protectionTest(factory);
        typeSwitchTest(factory);
        cloneTest(factory);

        Assert.assertTrue(OffHeapByteArray.alloc_counter == 0);
        Assert.assertTrue(OffHeapDoubleArray.alloc_counter == 0);
        Assert.assertTrue(OffHeapLongArray.alloc_counter == 0);
        Assert.assertTrue(OffHeapStringArray.alloc_counter == 0);
    }

    private boolean compareBuffers(Buffer buffer, Buffer buffer2) {
        if (buffer.size() != buffer2.size()) {
            return false;
        }
        for (int i = 0; i < buffer.size(); i++) {
            if (buffer.read(i) != buffer2.read(i)) {
                return false;
            }
        }
        return true;
    }

    private void saveLoadTest(StateChunkFactory factory) {
        //reset nb count
        nbCount = 0;

        StateChunk chunk = factory.create(this, null, null);

        //init chunk selectWith primitives
        chunk.set(0, Type.BOOL, true);
        chunk.set(1, Type.STRING, "hello");
        chunk.set(2, Type.DOUBLE, 1.0);
        chunk.set(3, Type.LONG, 1000l);
        chunk.set(4, Type.INT, 100);

        chunk.set(5, Type.INT, 1);
        chunk.set(5, Type.INT, null);

        Buffer buffer = org.mwg.core.utility.Buffer.newHeapBuffer();
        chunk.save(buffer);
        StateChunk chunk2 = factory.create(this, buffer, null);
        Buffer buffer2 = org.mwg.core.utility.Buffer.newHeapBuffer();
        chunk2.save(buffer2);

        Assert.assertTrue(compareBuffers(buffer, buffer2));

        for (int i = 0; i < 5; i++) {
            if (i == 1) {
                Assert.assertTrue(PrimitiveHelper.equals(chunk.get(i).toString(), chunk2.get(i).toString()));
            } else {
                Assert.assertTrue(chunk.get(i).equals(chunk2.get(i)));
            }
        }

        //init chunk selectWith arrays
        chunk.set(5, Type.LONG_ARRAY, new long[]{0, 1, 2, 3, 4});
        chunk.set(6, Type.DOUBLE_ARRAY, new double[]{0.1, 1.1, 2.1, 3.1, 4.1});
        chunk.set(7, Type.INT_ARRAY, new int[]{0, 1, 2, 3, 4});

        buffer.free();
        buffer = org.mwg.core.utility.Buffer.newHeapBuffer();

        chunk.save(buffer);
        free(chunk2);
        chunk2 = factory.create(this, buffer, null);

        buffer2.free();
        buffer2 = org.mwg.core.utility.Buffer.newHeapBuffer();
        chunk2.save(buffer2);

        Assert.assertTrue(compareBuffers(buffer, buffer2));

        //init chunk selectWith some maps
        LongLongMap long2longMap = (LongLongMap) chunk.getOrCreate(8, Type.LONG_LONG_MAP);
        long2longMap.put(1, 1);
        long2longMap.put(Constants.END_OF_TIME, Constants.END_OF_TIME);
        long2longMap.put(Constants.BEGINNING_OF_TIME, Constants.BEGINNING_OF_TIME);

        StringLongMap string2longMap = (StringLongMap) chunk.getOrCreate(9, Type.STRING_LONG_MAP);
        string2longMap.put("1", 1);
        string2longMap.put(Constants.END_OF_TIME + "", Constants.END_OF_TIME);
        string2longMap.put(Constants.BEGINNING_OF_TIME + "", Constants.BEGINNING_OF_TIME);

        LongLongArrayMap long2longArrayMap = (LongLongArrayMap) chunk.getOrCreate(10, Type.LONG_LONG_ARRAY_MAP);
        long2longArrayMap.put(1, 1);
        long2longArrayMap.put(Constants.END_OF_TIME, Constants.END_OF_TIME);
        long2longArrayMap.put(Constants.BEGINNING_OF_TIME, Constants.BEGINNING_OF_TIME);
        long2longArrayMap.put(Constants.BEGINNING_OF_TIME, Constants.END_OF_TIME);

        buffer.free();
        buffer = org.mwg.core.utility.Buffer.newHeapBuffer();
        chunk.save(buffer);
        free(chunk2);
        chunk2 = factory.create(this, buffer, null);

        buffer2.free();
        buffer2 = org.mwg.core.utility.Buffer.newHeapBuffer();
        chunk2.save(buffer2);

        Assert.assertTrue(((StringLongMap) chunk2.get(9)).size() == 3);
        Assert.assertTrue(((LongLongMap) chunk2.get(8)).size() == 3);
        Assert.assertTrue(((LongLongArrayMap) chunk2.get(10)).size() == 4);

        Assert.assertTrue(compareBuffers(buffer, buffer2));
        Assert.assertTrue(1 == nbCount);

        //force reHash
        for (int i = 0; i < 10; i++) {
            chunk.set(1000 + i, Type.INT, i);
        }

        for (int i = 0; i < 10; i++) {
            Assert.assertTrue(chunk.get(1000 + i).equals(i));
        }

        StateChunk chunk3 = factory.create(this, null, chunk);
        Buffer buffer3 = org.mwg.core.utility.Buffer.newHeapBuffer();
        chunk3.save(buffer3);

        buffer.free();
        buffer = org.mwg.core.utility.Buffer.newHeapBuffer();
        chunk.save(buffer);
        Assert.assertTrue(compareBuffers(buffer, buffer3));

        buffer3.free();
        buffer2.free();
        buffer.free();

        free(chunk3);
        free(chunk2);
        free(chunk);

    }

    private void free(StateChunk chunk) {
        if (chunk instanceof OffHeapChunk) {
            OffHeapStateChunk.free(((OffHeapChunk) chunk).addr());
        }
    }

    private void protectionTest(StateChunkFactory factory) {

        StateChunk chunk = factory.create(this, null, null);

        //boolean protection test
        //protectionMethod(chunk, Type.BOOL, null, true);
        protectionMethod(chunk, Type.BOOL, true, false);
        protectionMethod(chunk, Type.BOOL, "Hello", true);

        //protectionMethod(chunk, Type.DOUBLE, null, true);
        protectionMethod(chunk, Type.DOUBLE, 0.5d, false);
        protectionMethod(chunk, Type.DOUBLE, "Hello", true);

        //protectionMethod(chunk, Type.LONG, null, true);
        protectionMethod(chunk, Type.LONG, 100000000l, false);
        protectionMethod(chunk, Type.LONG, 100000000, false);
        protectionMethod(chunk, Type.LONG, "Hello", true);

        //protectionMethod(chunk, Type.INT, null, true);
        protectionMethod(chunk, Type.INT, 10, false);
        protectionMethod(chunk, Type.INT, "Hello", true);

        //protectionMethod(chunk, Type.STRING, null, false);
        protectionMethod(chunk, Type.STRING, "Hello", false);
        protectionMethod(chunk, Type.STRING, true, true);

        //arrays
        protectionMethod(chunk, Type.DOUBLE_ARRAY, new double[]{0.1d, 0.2d, 0.3d}, false);
        protectionMethod(chunk, Type.DOUBLE_ARRAY, "hello", true);

        protectionMethod(chunk, Type.LONG_ARRAY, new long[]{10l, 100l, 1000l}, false);
        protectionMethod(chunk, Type.LONG_ARRAY, "hello", true);

        protectionMethod(chunk, Type.INT_ARRAY, new int[]{10, 100, 1000}, false);
        protectionMethod(chunk, Type.INT_ARRAY, "hello", true);

        //maps
        protectionMethod(chunk, Type.STRING_LONG_MAP, "hello", true);
        protectionMethod(chunk, Type.LONG_LONG_MAP, "hello", true);
        protectionMethod(chunk, Type.LONG_LONG_ARRAY_MAP, "hello", true);

        free(chunk);

    }

    private void typeSwitchTest(StateChunkFactory factory) {
        StateChunk chunk = factory.create(this, null, null);

        //init primitives
        chunk.set(0, Type.BOOL, true);
        chunk.set(1, Type.STRING, "hello");
        chunk.set(2, Type.LONG, 1000l);
        chunk.set(3, Type.INT, 100);
        chunk.set(4, Type.DOUBLE, 1.0);
        //init arrays
        chunk.set(5, Type.DOUBLE_ARRAY, new double[]{1.0, 2.0, 3.0});
        chunk.set(6, Type.LONG_ARRAY, new long[]{1, 2, 3});
        chunk.set(7, Type.INT_ARRAY, new int[]{1, 2, 3});
        //init maps
        ((LongLongMap) chunk.getOrCreate(8, Type.LONG_LONG_MAP)).put(100, 100);
        ((LongLongArrayMap) chunk.getOrCreate(9, Type.LONG_LONG_ARRAY_MAP)).put(100, 100);
        ((StringLongMap) chunk.getOrCreate(10, Type.STRING_LONG_MAP)).put("100", 100);

        //ok now switch all types

        //switch primitives
        chunk.set(10, Type.BOOL, true);
        Assert.assertTrue(chunk.getType(10) == Type.BOOL);
        Assert.assertTrue((boolean) chunk.get(10));

        chunk.set(0, Type.STRING, "hello");
        Assert.assertTrue(chunk.getType(0) == Type.STRING);
        Assert.assertTrue(PrimitiveHelper.equals(chunk.get(0).toString(), "hello"));

        chunk.set(1, Type.LONG, 1000l);
        Assert.assertTrue(chunk.getType(1) == Type.LONG);
        Assert.assertTrue((long) chunk.get(1) == 1000l);

        chunk.set(2, Type.INT, 100);
        Assert.assertTrue(chunk.getType(2) == Type.INT);
        Assert.assertTrue((int) chunk.get(2) == 100);

        chunk.set(3, Type.DOUBLE, 1.0);
        Assert.assertTrue(chunk.getType(3) == Type.DOUBLE);
        Assert.assertTrue((double) chunk.get(3) == 1.0);

        //switch arrays
        chunk.set(4, Type.DOUBLE_ARRAY, new double[]{1.0, 2.0, 3.0});
        Assert.assertTrue(chunk.getType(4) == Type.DOUBLE_ARRAY);
        Assert.assertTrue(((double[]) chunk.get(4))[0] == 1.0);

        chunk.set(5, Type.LONG_ARRAY, new long[]{1, 2, 3});
        Assert.assertTrue(chunk.getType(5) == Type.LONG_ARRAY);
        Assert.assertTrue(((long[]) chunk.get(5))[0] == 1);

        chunk.set(6, Type.INT_ARRAY, new int[]{1, 2, 3});
        Assert.assertTrue(chunk.getType(6) == Type.INT_ARRAY);
        Assert.assertTrue(((int[]) chunk.get(6))[0] == 1);

        //switch maps
        ((LongLongMap) chunk.getOrCreate(7, Type.LONG_LONG_MAP)).put(100, 100);
        ((LongLongArrayMap) chunk.getOrCreate(8, Type.LONG_LONG_ARRAY_MAP)).put(100, 100);
        ((StringLongMap) chunk.getOrCreate(9, Type.STRING_LONG_MAP)).put("100", 100);

        free(chunk);

    }

    private void cloneTest(StateChunkFactory factory) {
        StateChunk chunk = factory.create(this, null, null);

        //init primitives
        chunk.set(0, Type.BOOL, true);
        chunk.set(1, Type.STRING, "hello");
        chunk.set(2, Type.LONG, 1000l);
        chunk.set(3, Type.INT, 100);
        chunk.set(4, Type.DOUBLE, 1.0);
        //init arrays
        chunk.set(5, Type.DOUBLE_ARRAY, new double[]{1.0, 2.0, 3.0});
        chunk.set(6, Type.LONG_ARRAY, new long[]{1, 2, 3});
        chunk.set(7, Type.INT_ARRAY, new int[]{1, 2, 3});
        //init maps
        ((LongLongMap) chunk.getOrCreate(8, Type.LONG_LONG_MAP)).put(100, 100);
        ((LongLongArrayMap) chunk.getOrCreate(9, Type.LONG_LONG_ARRAY_MAP)).put(100, 100);
        ((StringLongMap) chunk.getOrCreate(10, Type.STRING_LONG_MAP)).put("100", 100);

        //clone the chunk
        StateChunk chunk2 = factory.create(this, null, chunk);

        //test primitives
        Assert.assertTrue(chunk2.getType(0) == Type.BOOL);
        Assert.assertTrue((boolean) chunk.get(0));

        Assert.assertTrue(chunk2.getType(1) == Type.STRING);
        Assert.assertTrue(PrimitiveHelper.equals(chunk2.get(1).toString(), "hello"));

        Assert.assertTrue(chunk2.getType(2) == Type.LONG);
        Assert.assertTrue((long) chunk2.get(2) == 1000l);

        Assert.assertTrue(chunk2.getType(3) == Type.INT);
        Assert.assertTrue((int) chunk2.get(3) == 100);

        Assert.assertTrue(chunk2.getType(4) == Type.DOUBLE);
        Assert.assertTrue((double) chunk2.get(4) == 1.0);

        //test arrays
        Assert.assertTrue(chunk2.getType(5) == Type.DOUBLE_ARRAY);
        Assert.assertTrue(((double[]) chunk2.get(5))[0] == 1.0);

        Assert.assertTrue(chunk2.getType(6) == Type.LONG_ARRAY);
        Assert.assertTrue(((long[]) chunk2.get(6))[0] == 1);

        Assert.assertTrue(chunk2.getType(7) == Type.INT_ARRAY);
        Assert.assertTrue(((int[]) chunk2.get(7))[0] == 1);

        //test maps
        Assert.assertTrue(((LongLongMap) chunk2.get(8)).get(100) == 100);
        Assert.assertTrue(((LongLongArrayMap) chunk2.get(9)).get(100)[0] == 100);
        Assert.assertTrue(((StringLongMap) chunk2.get(10)).getValue("100") == 100);

        //now we test the co-evolution of clone

        //STRINGS
        chunk.set(1, Type.STRING, "helloPast");
        Assert.assertTrue(PrimitiveHelper.equals(chunk.get(1).toString(), "helloPast"));
        Assert.assertTrue(PrimitiveHelper.equals(chunk2.get(1).toString(), "hello"));

        chunk2.set(1, Type.STRING, "helloFuture");
        Assert.assertTrue(PrimitiveHelper.equals(chunk2.get(1).toString(), "helloFuture"));
        Assert.assertTrue(PrimitiveHelper.equals(chunk.get(1).toString(), "helloPast"));

        //ARRAYS
        chunk2.set(5, Type.DOUBLE_ARRAY, new double[]{3.0, 4.0, 5.0});
        Assert.assertTrue(((double[]) chunk2.get(5))[0] == 3.0);
        Assert.assertTrue(((double[]) chunk.get(5))[0] == 1.0);

        chunk2.set(6, Type.LONG_ARRAY, new long[]{100, 200, 300});
        Assert.assertTrue(((long[]) chunk2.get(6))[0] == 100);
        Assert.assertTrue(((long[]) chunk.get(6))[0] == 1);

        chunk2.set(7, Type.INT_ARRAY, new int[]{100, 200, 300});
        Assert.assertTrue(((int[]) chunk2.get(7))[0] == 100);
        Assert.assertTrue(((int[]) chunk.get(7))[0] == 1);

        //MAPS
        ((LongLongMap) chunk2.get(8)).put(100, 200);
        Assert.assertTrue(((LongLongMap) chunk2.get(8)).get(100) == 200);
        Assert.assertTrue(((LongLongMap) chunk.get(8)).get(100) == 100);

        ((LongLongArrayMap) chunk2.get(9)).put(100, 200);
        Assert.assertTrue(((LongLongArrayMap) chunk2.get(9)).get(100)[0] == 200);
        Assert.assertTrue(((LongLongArrayMap) chunk2.get(9)).get(100)[1] == 100);
        Assert.assertTrue(((LongLongArrayMap) chunk.get(9)).get(100)[0] == 100);

        ((StringLongMap) chunk2.get(10)).put("100", 200);
        Assert.assertTrue(((StringLongMap) chunk2.get(10)).getValue("100") == 200);
        Assert.assertTrue(((StringLongMap) chunk.get(10)).getValue("100") == 100);

        // add something new instead of replacing something -> triggers the shallow copy of the clone
        chunk2.set(11, Type.STRING, "newString");
        Assert.assertTrue(chunk2.get(11).equals("newString"));


        free(chunk);
        free(chunk2);

    }


    private void protectionMethod(StateChunk chunk, byte elemType, Object elem, boolean shouldCrash) {
        boolean hasCrash = false;
        try {
            chunk.set(0, elemType, elem);
        } catch (Throwable e) {
            hasCrash = true;
        }
        Assert.assertTrue(hasCrash == shouldCrash);
    }

    @Override
    public void declareDirty(Chunk chunk) {
        nbCount++;
        //simulate space management
        if (chunk instanceof HeapChunk) {
            ((HeapChunk) chunk).setFlags(Constants.DIRTY_BIT, 0);
        } else if (chunk instanceof OffHeapChunk) {
            long addr = ((OffHeapChunk) chunk).addr();
            OffHeapLongArray.set(addr, Constants.OFFHEAP_CHUNK_INDEX_FLAGS, Constants.DIRTY_BIT);
        }

    }

    @Override
    public Graph graph() {
        return null;
    }
}