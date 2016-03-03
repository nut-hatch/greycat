package org.mwdb.chunk;

public interface KChunkSpace {

    /**
     * Create KChunk, out of the space, not marked, used as a factory
     */
    KChunk create(long world, long time, long id, short type);

    /**
     * Get and mark chunk for the association of keys
     */
    KChunk getAndMark(long world, long time, long id);

    /**
     * Insert the chunk into the space and mark it before as used
     */
    KChunk putAndMark(KChunk elem);

    /**
     * UnMark chunk for the association of keys
     */
    void unmark(long world, long time, long id);

    /**
     * UnMark chunk
     */
    void unmarkChunk(KChunk chunk);

    /**
     * Declare the chunk as dirty
     */
    void declareDirty(KChunk elem);


    //void remove(long universe, long time, long obj);
    //KObjectChunk clone(KObjectChunk previousElement, long newUniverse, long newTime, long newObj, KMetaModel metaModel);

    void clear();

    void free();

    int size();


    KChunkIterator detachDirties();

    // void declareDirty(KChunk dirtyChunk);

    // void printDebug(KMetaModel p_metaModel);

}
