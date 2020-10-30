package org.terabit.db;

import org.terabit.common.Log;
import org.ethereum.datasource.DbSettings;
import org.ethereum.datasource.DbSource;
import org.iq80.leveldb.*;
import org.iq80.leveldb.impl.Iq80DBFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LevelDbDataSource implements DbSource<byte[]> {
    private String name;
    private DB db;
    private Iq80DBFactory factory = new Iq80DBFactory();
    private boolean isAlive;

    private Log logger = new Log("LevelDb");

    private ReadWriteLock resetDbLock = new ReentrantReadWriteLock();

    public LevelDbDataSource(String name) {
        this.name = name;

        this.init();
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void init() {
        init(null);
    }

    @Override
    public void init(DbSettings settings) {
        resetDbLock.writeLock().lock();
        try {
            if (isAlive) return;

            if (name == null) throw new NullPointerException("no name set to the db");

            Options options = new Options();
            options.createIfMissing(true);
            options.compressionType(CompressionType.NONE);
            options.blockSize(10 * 1024 * 1024);
            options.writeBufferSize(10 * 1024 * 1024);
            options.cacheSize(0);
            options.paranoidChecks(true);
            options.verifyChecksums(true);
            options.maxOpenFiles(32);

            try {
                logger.log("Opening database");
                final Path dbPath = getPath();
                if (!Files.isSymbolicLink(dbPath.getParent())) Files.createDirectories(dbPath.getParent());

                logger.log("Initializing new or existing database: '{" + name + "}'");
                try {
                    db = factory.open(new File(dbPath.toString()), options);
                } catch (IOException e) {
                    if (e.getMessage().contains("Corruption:")) {
                        logger.warn("Problem initializing database.", e);
                        logger.info("LevelDB database must be corrupted. Trying to repair. Could take some time.");
                        factory.repair(dbPath.toFile(), options);
                        logger.info("Repair finished. Opening database again.");
                        db = factory.open(dbPath.toFile(), options);
                    } else {
                        throw e;
                    }
                }

                isAlive = true;
            } catch (IOException ioe) {
                logger.err(ioe.getMessage(), ioe);
                throw new RuntimeException("Can't initialize database", ioe);
            }
            logger.log("<~ LevelDbDataSource.init(): " + name);
        } finally {
            resetDbLock.writeLock().unlock();
        }
    }

    @Override
    public boolean isAlive() {
        return isAlive;
    }

    @Override
    public boolean flush() {
        return false;
    }

    @Override
    public void reset() {
        close();
        init(null);
    }

    @Override
    public byte[] prefixLookup(byte[] key, int prefixBytes) {
        throw new RuntimeException("LevelDbDataSource.prefixLookup() is not supported");
    }

    public void destroyDB(File fileLocation) {
        resetDbLock.writeLock().lock();
        try {
            logger.log("Destroying existing database: " + fileLocation);
            Options options = new Options();
            try {
                factory.destroy(fileLocation, options);
            } catch (IOException e) {
                logger.err(e.getMessage(), e);
            }
        } finally {
            resetDbLock.writeLock().unlock();
        }
    }

    private Path getPath() {
        return Paths.get("./dbs/" + name + ".db");
    }

    public byte[] get(byte[] key) {
        resetDbLock.readLock().lock();
        try {
            try {
                byte[] ret = db.get(key);
                return ret;
            } catch (DBException e) {
                logger.warn("Exception. Retrying again...", e);
                byte[] ret = db.get(key);
                return ret;
            }
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    public void put(byte[] key, byte[] value) {
        resetDbLock.readLock().lock();
        try {
            db.put(key, value);
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    public void delete(byte[] key) {
        resetDbLock.readLock().lock();
        try {
            db.delete(key);
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    public Set<byte[]> keys() {
        resetDbLock.readLock().lock();
        try {
            try (DBIterator iterator = db.iterator()) {
                Set<byte[]> result = new HashSet<>();
                for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                    result.add(iterator.peekNext().getKey());
                }
                return result;
            } catch (IOException e) {
                logger.err("Unexpected", e);
                throw new RuntimeException(e);
            }
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    private void updateBatchInternal(Map<byte[], byte[]> rows) throws IOException {
        try (WriteBatch batch = db.createWriteBatch()) {
            for (Map.Entry<byte[], byte[]> entry : rows.entrySet()) {
                if (entry.getValue() == null) {
                    batch.delete(entry.getKey());
                } else {
                    batch.put(entry.getKey(), entry.getValue());
                }
            }
            db.write(batch);
        }
    }

    public void updateBatch(Map<byte[], byte[]> rows) {
        resetDbLock.readLock().lock();
        try {
            try {
                updateBatchInternal(rows);
            } catch (Exception e) {
                logger.err("Error, retrying one more time...", e);
                try {
                    updateBatchInternal(rows);
                } catch (Exception e1) {
                    logger.err("Error", e);
                    throw new RuntimeException(e);
                }
            }
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    public void close() {
        resetDbLock.writeLock().lock();
        try {
            if (!isAlive) return;

            try {
                logger.log("Close db: {" + name + "}");
                db.close();

                isAlive = false;
            } catch (IOException e) {
                logger.err("Failed to find the db file on the close: {" + name + "} ");
            }
        } finally {
            resetDbLock.writeLock().unlock();
        }
    }
}
