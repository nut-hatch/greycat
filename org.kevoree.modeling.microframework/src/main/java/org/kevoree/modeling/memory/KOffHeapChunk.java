package org.kevoree.modeling.memory;

import org.kevoree.modeling.memory.space.impl.OffHeapChunkSpace;

public interface KOffHeapChunk extends KChunk {

    long memoryAddress();

    void setMemoryAddress(long address);

}
