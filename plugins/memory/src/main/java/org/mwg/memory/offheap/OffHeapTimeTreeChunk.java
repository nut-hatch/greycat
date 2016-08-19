package org.mwg.memory.offheap;

import org.mwg.Constants;
import org.mwg.chunk.ChunkType;
import org.mwg.chunk.TimeTreeChunk;
import org.mwg.chunk.TreeWalker;
import org.mwg.struct.Buffer;
import org.mwg.utility.Base64;

class OffHeapTimeTreeChunk implements TimeTreeChunk {

    private static final int META_SIZE = 3;
    private static final byte TRUE = 1;
    private static final byte FALSE = 0;

    private static final int LOCK = 0;
    private static final int MAGIC = 1;
    private static final int METAS = 2;
    private static final int K = 3;
    private static final int COLORS = 4;
    private static final int SIZE = 5;
    private static final int CAPACITY = 6;
    private static final int DIRTY = 7;
    private static final int ROOT = 8;

    private static final int CHUNK_SIZE = 9;

    private final OffHeapChunkSpace space;
    private final long index;
    private final long addr;

    private long kPtr;
    private long metaPtr;
    private long colorsPtr;


    OffHeapTimeTreeChunk(final OffHeapChunkSpace p_space, final long p_index) {
        space = p_space;
        index = p_index;
        long temp_addr = space.addrByIndex(index);
        if (temp_addr == OffHeapConstants.OFFHEAP_NULL_PTR) {
            temp_addr = OffHeapLongArray.allocate(CHUNK_SIZE);
            space.setAddrByIndex(index, temp_addr);
            //init the initial values
            OffHeapLongArray.set(temp_addr, LOCK, 0);
            OffHeapLongArray.set(temp_addr, MAGIC, 0);
            OffHeapLongArray.set(temp_addr, CAPACITY, 0);
            OffHeapLongArray.set(temp_addr, SIZE, 0);
            OffHeapLongArray.set(temp_addr, DIRTY, 0);
            OffHeapLongArray.set(temp_addr, ROOT, -1);
            OffHeapLongArray.set(temp_addr, K, OffHeapConstants.OFFHEAP_NULL_PTR);
            OffHeapLongArray.set(temp_addr, COLORS, OffHeapConstants.OFFHEAP_NULL_PTR);
            OffHeapLongArray.set(temp_addr, METAS, OffHeapConstants.OFFHEAP_NULL_PTR);
        }
        addr = temp_addr;
    }

    public static void free(final long addr) {
        if (addr != OffHeapConstants.OFFHEAP_NULL_PTR) {
            OffHeapLongArray.free(OffHeapLongArray.get(addr, K));
            OffHeapLongArray.free(OffHeapLongArray.get(addr, COLORS));
            OffHeapLongArray.free(OffHeapLongArray.get(addr, METAS));
            OffHeapLongArray.free(addr);
        }
    }

    @Override
    public final long world() {
        return space.worldByIndex(index);
    }

    @Override
    public final long time() {
        return space.timeByIndex(index);
    }

    @Override
    public final long id() {
        return space.idByIndex(index);
    }

    @Override
    public final long size() {
        return OffHeapLongArray.get(addr, SIZE);
    }

    @Override
    public final long index() {
        return index;
    }

    @Override
    public final long magic() {
        return OffHeapLongArray.get(addr, MAGIC);
    }

    @Override
    public synchronized final void range(final long startKey, final long endKey, final long maxElements, final TreeWalker walker) {
        //lock and load fromVar main memory
        long nbElements = 0;
        long indexEnd = internal_previousOrEqual_index(endKey);
        while (indexEnd != -1 && key(indexEnd) >= startKey && nbElements < maxElements) {
            walker.elem(key(indexEnd));
            nbElements++;
            indexEnd = previous(indexEnd);
        }
    }

    @Override
    public synchronized final void save(Buffer buffer) {
        final long size = OffHeapLongArray.get(addr, SIZE);
        final long k_addr = OffHeapLongArray.get(addr, K);
        Base64.encodeLongToBuffer(size, buffer);
        buffer.write(Constants.CHUNK_SEP);
        boolean isFirst = true;
        for (long i = 0; i < size; i++) {
            if (!isFirst) {
                buffer.write(Constants.CHUNK_SUB_SEP);
            } else {
                isFirst = false;
            }
            Base64.encodeLongToBuffer(OffHeapLongArray.get(k_addr, i), buffer);
        }
        OffHeapLongArray.set(addr, DIRTY, 0);
    }

    @Override
    public synchronized final void load(Buffer buffer) {
        if (buffer == null || buffer.length() == 0) {
            return;
        }
        final boolean initial = (OffHeapLongArray.get(addr, K) == OffHeapConstants.OFFHEAP_NULL_PTR);
        boolean isDirty = false;
        long cursor = 0;
        long previous = 0;
        long payloadSize = buffer.length();
        while (cursor < payloadSize) {
            byte current = buffer.read(cursor);
            if (current == Constants.CHUNK_SUB_SEP) {
                isDirty = isDirty || internal_insert(Base64.decodeToLongWithBounds(buffer, previous, cursor));
                previous = cursor + 1;
            } else if (current == Constants.CHUNK_SEP) {
                reallocate(OffHeapLongArray.get(addr, CAPACITY), Base64.decodeToLongWithBounds(buffer, previous, cursor));
                previous = cursor + 1;
            }
            cursor++;
        }
        isDirty = isDirty || internal_insert(Base64.decodeToLongWithBounds(buffer, previous, cursor));
        if (isDirty && !initial && OffHeapLongArray.get(addr, DIRTY) != 1) {
            OffHeapLongArray.set(addr, DIRTY, 1);
            if (space != null) {
                space.notifyUpdate(index);
            }
        }
    }

    @Override
    public synchronized final long previousOrEqual(long key) {
        //lock and load fromVar main memory
        long resultKey;
        long result = internal_previousOrEqual_index(key);
        if (result != -1) {
            resultKey = key(result);
        } else {
            resultKey = Constants.NULL_LONG;
        }
        return resultKey;
    }

    @Override
    public synchronized final void insert(final long p_key) {
        if (internal_insert(p_key)) {
            internal_set_dirty();
        }
    }

    @Override
    public synchronized final void unsafe_insert(final long p_key) {
        internal_insert(p_key);
    }

    @Override
    public final byte chunkType() {
        return ChunkType.TIME_TREE_CHUNK;
    }

    @Override
    public final void clearAt(long max) {
        while (!OffHeapLongArray.compareAndSwap(addr, LOCK, 0, 1)) ;
        try {
            // ptrConsistency();
            long previousKeys = OffHeapLongArray.get(addr, K);
            long previousMetas = OffHeapLongArray.get(addr, METAS);
            long previousColors = OffHeapLongArray.get(addr, COLORS);
            long previousSize = OffHeapLongArray.get(addr, SIZE);

            //reset
            long capacity = Constants.MAP_INITIAL_CAPACITY;
            //init k array
            kPtr = OffHeapLongArray.allocate(capacity);
            OffHeapLongArray.set(addr, K, kPtr);
            //init meta array
            metaPtr = OffHeapLongArray.allocate(capacity * META_SIZE);
            OffHeapLongArray.set(addr, METAS, metaPtr);
            //init colors array
            colorsPtr = OffHeapByteArray.allocate(capacity);
            OffHeapLongArray.set(addr, COLORS, colorsPtr);

            OffHeapLongArray.set(addr, SIZE, 0);
            OffHeapLongArray.set(addr, ROOT, -1);
            OffHeapLongArray.set(addr, MAGIC, OffHeapLongArray.get(addr, MAGIC) + 1);

            for (long i = 0; i < previousSize; i++) {
                long currentVal = OffHeapLongArray.get(previousKeys, i);
                if (currentVal < max) {
                    internal_insert(OffHeapLongArray.get(previousKeys, i));
                }
            }

            OffHeapLongArray.free(previousKeys);
            OffHeapLongArray.free(previousMetas);
            OffHeapByteArray.free(previousColors);
        } finally {
            //Free OffHeap lock
            if (!OffHeapLongArray.compareAndSwap(addr, LOCK, 1, 0)) {
                throw new RuntimeException("CAS Error !!!");
            }
        }
        internal_set_dirty();
    }

    private void reallocate(long previousCapacity, long newCapacity) {
        if (previousCapacity < newCapacity) {
            long k_addr = OffHeapLongArray.get(addr, K);
            long next_k_addr;
            if (k_addr == OffHeapConstants.OFFHEAP_NULL_PTR) {
                next_k_addr = OffHeapLongArray.allocate(newCapacity);
            } else {
                next_k_addr = OffHeapLongArray.reallocate(k_addr, previousCapacity, newCapacity);
            }
            if (k_addr != next_k_addr) {
                OffHeapLongArray.set(addr, K, next_k_addr);
            }
            long colors_addr = OffHeapLongArray.get(addr, COLORS);
            long next_colors_addr;
            if (colors_addr == OffHeapConstants.OFFHEAP_NULL_PTR) {
                next_colors_addr = OffHeapByteArray.allocate(newCapacity);
            } else {
                next_colors_addr = OffHeapByteArray.reallocate(colors_addr, previousCapacity, newCapacity);
            }
            if (colors_addr != next_colors_addr) {
                OffHeapLongArray.set(addr, COLORS, next_colors_addr);
            }
            long metas_addr = OffHeapLongArray.get(addr, METAS);
            long next_metas_addr;
            if (metas_addr == OffHeapConstants.OFFHEAP_NULL_PTR) {
                next_metas_addr = OffHeapByteArray.allocate(newCapacity);
            } else {
                next_metas_addr = OffHeapByteArray.reallocate(metas_addr, previousCapacity, newCapacity);
            }
            if (metas_addr != next_metas_addr) {
                OffHeapLongArray.set(addr, METAS, next_metas_addr);
            }
        }
    }

    private long key(long p_currentIndex) {
        if (p_currentIndex == -1) {
            return -1;
        }
        return OffHeapLongArray.get(kPtr, p_currentIndex);
    }

    private void setKey(long p_currentIndex, long p_paramIndex) {
        OffHeapLongArray.set(kPtr, p_currentIndex, p_paramIndex);
    }

    private long left(long p_currentIndex) {
        if (p_currentIndex == -1) {
            return -1;
        }
        return OffHeapLongArray.get(metaPtr, p_currentIndex * META_SIZE);
    }

    private void setLeft(long p_currentIndex, long p_paramIndex) {
        OffHeapLongArray.set(metaPtr, p_currentIndex * META_SIZE, p_paramIndex);
    }

    private long right(long p_currentIndex) {
        if (p_currentIndex == -1) {
            return -1;
        }
        return OffHeapLongArray.get(metaPtr, (p_currentIndex * META_SIZE) + 1);
    }

    private void setRight(long p_currentIndex, long p_paramIndex) {
        OffHeapLongArray.set(metaPtr, (p_currentIndex * META_SIZE) + 1, p_paramIndex);
    }

    private long parent(long p_currentIndex) {
        if (p_currentIndex == -1) {
            return -1;
        }
        return OffHeapLongArray.get(metaPtr, (p_currentIndex * META_SIZE) + 2);
    }

    private void setParent(long p_currentIndex, long p_paramIndex) {
        OffHeapLongArray.set(metaPtr, (p_currentIndex * META_SIZE) + 2, p_paramIndex);
    }

    private boolean color(long p_currentIndex) {
        if (p_currentIndex == -1) {
            return true;
        }
        return OffHeapByteArray.get(colorsPtr, p_currentIndex) == 1;
    }

    private void setColor(long p_currentIndex, boolean p_paramIndex) {
        if (p_paramIndex) {
            OffHeapByteArray.set(colorsPtr, p_currentIndex, (byte) 1);
        } else {
            OffHeapByteArray.set(colorsPtr, p_currentIndex, (byte) 0);
        }
    }

    private long grandParent(long p_currentIndex) {
        if (p_currentIndex == -1) {
            return -1;
        }
        if (parent(p_currentIndex) != -1) {
            return parent(parent(p_currentIndex));
        } else {
            return -1;
        }
    }

    private long sibling(long p_currentIndex) {
        if (parent(p_currentIndex) == -1) {
            return -1;
        } else {
            if (p_currentIndex == left(parent(p_currentIndex))) {
                return right(parent(p_currentIndex));
            } else {
                return left(parent(p_currentIndex));
            }
        }
    }

    private long uncle(long p_currentIndex) {
        if (parent(p_currentIndex) != -1) {
            return sibling(parent(p_currentIndex));
        } else {
            return -1;
        }
    }

    private long previous(long p_index) {
        long p = p_index;
        if (left(p) != -1) {
            p = left(p);
            while (right(p) != -1) {
                p = right(p);
            }
            return p;
        } else {
            if (parent(p) != -1) {
                if (p == right(parent(p))) {
                    return parent(p);
                } else {
                    while (parent(p) != -1 && p == left(parent(p))) {
                        p = parent(p);
                    }
                    return parent(p);
                }
            } else {
                return -1;
            }
        }
    }

    private void rotateLeft(long n) {
        long r = right(n);
        replaceNode(n, r);
        setRight(n, left(r));
        if (left(r) != -1) {
            setParent(left(r), n);
        }
        setLeft(r, n);
        setParent(n, r);
    }

    private void rotateRight(long n) {
        long l = left(n);
        replaceNode(n, l);
        setLeft(n, right(l));
        if (right(l) != -1) {
            setParent(right(l), n);
        }
        setRight(l, n);
        setParent(n, l);
    }

    private void replaceNode(long oldn, long newn) {
        if (parent(oldn) == -1) {
            OffHeapLongArray.set(addr, ROOT, newn);
        } else {
            if (oldn == left(parent(oldn))) {
                setLeft(parent(oldn), newn);
            } else {
                setRight(parent(oldn), newn);
            }
        }
        if (newn != -1) {
            setParent(newn, parent(oldn));
        }
    }

    private void insertCase1(long n) {
        if (parent(n) == -1) {
            setColor(n, true);
        } else {
            insertCase2(n);
        }
    }

    private void insertCase2(long n) {
        if (!color(parent(n))) {
            insertCase3(n);
        }
    }

    private void insertCase3(long n) {
        if (!color(uncle(n))) {
            setColor(parent(n), true);
            setColor(uncle(n), true);
            setColor(grandParent(n), false);
            insertCase1(grandParent(n));
        } else {
            insertCase4(n);
        }
    }

    private void insertCase4(long n_n) {
        long n = n_n;
        if (n == right(parent(n)) && parent(n) == left(grandParent(n))) {
            rotateLeft(parent(n));
            n = left(n);
        } else {
            if (n == left(parent(n)) && parent(n) == right(grandParent(n))) {
                rotateRight(parent(n));
                n = right(n);
            }
        }
        insertCase5(n);
    }

    private void insertCase5(long n) {
        setColor(parent(n), true);
        setColor(grandParent(n), false);
        if (n == left(parent(n)) && parent(n) == left(grandParent(n))) {
            rotateRight(grandParent(n));
        } else {
            rotateLeft(grandParent(n));
        }
    }

    private long internal_previousOrEqual_index(long p_key) {
        long p = OffHeapLongArray.get(addr, ROOT);
        if (p == -1) {
            return p;
        }
        while (p != -1) {
            if (p_key == key(p)) {
                return p;
            }
            if (p_key > key(p)) {
                if (right(p) != -1) {
                    p = right(p);
                } else {
                    return p;
                }
            } else {
                if (left(p) != -1) {
                    p = left(p);
                } else {
                    long parent = parent(p);
                    long ch = p;
                    while (parent != -1 && ch == left(parent)) {
                        ch = parent;
                        parent = parent(parent);
                    }
                    return parent;
                }
            }
        }
        return -1;
    }

    private boolean internal_insert(long p_key) {
/*

        if (_k == null || _k.length == _size) {
            int length = _size;
            if (length == 0) {
                length = Constants.MAP_INITIAL_CAPACITY;
            } else {
                length = length * 2;
            }
            reallocate(length);
        }
        int newIndex = _size;
        if (newIndex == 0) {
            setKey(newIndex, p_key);
            setColor(newIndex, false);
            setLeft(newIndex, -1);
            setRight(newIndex, -1);
            setParent(newIndex, -1);
            _root = newIndex;
            _size = 1;
        } else {
            int n = _root;
            while (true) {
                if (p_key == key(n)) {
                    return false;
                } else if (p_key < key(n)) {
                    if (left(n) == -1) {
                        setKey(newIndex, p_key);
                        setColor(newIndex, false);
                        setLeft(newIndex, -1);
                        setRight(newIndex, -1);
                        setParent(newIndex, -1);
                        setLeft(n, newIndex);
                        _size++;
                        break;
                    } else {
                        n = left(n);
                    }
                } else {
                    if (right(n) == -1) {
                        setKey(newIndex, p_key);
                        setColor(newIndex, false);
                        setLeft(newIndex, -1);
                        setRight(newIndex, -1);
                        setParent(newIndex, -1);
                        setRight(n, newIndex);
                        _size++;
                        break;
                    } else {
                        n = right(n);
                    }
                }
            }
            setParent(newIndex, n);
        }
        insertCase1(newIndex);
        */
        return true;
    }

    private void internal_set_dirty() {
        OffHeapLongArray.set(addr, MAGIC, OffHeapLongArray.get(addr, MAGIC) + 1);
        if (space != null && OffHeapLongArray.get(addr, DIRTY) != 1) {
            OffHeapLongArray.set(addr, DIRTY, 1);
            space.notifyUpdate(index);
        }
    }

}