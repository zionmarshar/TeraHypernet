package org.ethereum.datasource;

import org.terabit.db.LevelDbDataSource;
import org.ethereum.util.ByteUtil;
import org.spongycastle.util.encoders.Hex;

import java.util.AbstractList;

import static org.terabit.core.BlockKt.getBlockHashKey;

public class DataSourceArray extends AbstractList<byte[]> {
    private LevelDbDataSource src; //Only blocks are supported for now
    private static final byte[] SIZE_KEY = Hex.decode("FFFFFFFFFFFFFFFF");
    private int size = -1;

    public DataSourceArray(LevelDbDataSource src) {
        this.src = src;
    }

    @Override
    public synchronized byte[] set(int idx, byte[] value) {
        if (idx >= size()) {
            setSize(idx + 1);
        }
        src.put(ByteUtil.intToBytes(idx), value);
        return value;
    }

    @Override
    public synchronized void add(int index, byte[] element) {
        set(index, element);
    }

    @Override
    public synchronized byte[] remove(int index) {
        throw new RuntimeException("Not supported yet.");
    }

    //block height is equal to the index of chain
    @Override
    public synchronized byte[] get(int idx) {
        if (idx < 0 || idx >= size()) throw new IndexOutOfBoundsException(idx + " > " + size);
        return src.get(getBlockHashKey(idx));
    }

    @Override
    public synchronized int size() {
        if (size < 0) {
            byte[] sizeBB = src.get(SIZE_KEY);
            size = sizeBB == null ? 0 : ByteUtil.byteArrayToInt(sizeBB);
        }
        return size;
    }

    public synchronized void setSize(int newSize) {
        size = newSize;
        src.put(SIZE_KEY, ByteUtil.intToBytes(newSize));
    }
}
