package tachyon.worker.hierarchy;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import tachyon.TestUtils;
import tachyon.UnderFileSystem;
import tachyon.client.BlockHandler;
import tachyon.master.BlockInfo;
import tachyon.util.CommonUtils;

public class AllocateStrategyTest {
  private final StorageDir[] mStorageDirs = new StorageDir[3];
  private final long mUserId = 1;
  private final long[] mCapacities = new long[] {1000, 1100, 1200};

  @Before
  public final void before() throws IOException {
    String tachyonHome =
        File.createTempFile("Tachyon", "").getAbsoluteFile() + "U" + System.currentTimeMillis();
    String workerDirFolder = tachyonHome + "/ramdisk";
    String[] dirPaths = "/dir1,/dir2,/dir3".split(",");
    for (int i = 0; i < 3; i++) {
      mStorageDirs[i] =
          new StorageDir(i + 1, workerDirFolder + dirPaths[i], mCapacities[i], "/data", "/user",
              null);
      initializeStorageDir(mStorageDirs[i], mUserId);
    }
  }

  private void createBlockFile(StorageDir dir, long blockId, int blockSize) throws IOException {
    byte[] buf = TestUtils.getIncreasingByteArray(blockSize);

    BlockHandler bhSrc =
        BlockHandler.get(dir.getUserTempFilePath(mUserId, blockId));
    try {
      bhSrc.append(0, ByteBuffer.wrap(buf));
    } finally {
      bhSrc.close();
    }
    dir.requestSpace(mUserId, blockSize);
    dir.cacheBlock(mUserId, blockId);
  }

  @Test
  public void AllocateMaxFreeTest() throws IOException {
    AllocateStrategy allocator = 
        AllocateStrategies.getAllocateStrategy(AllocateStrategyType.MAX_FREE);
    StorageDir storageDir = allocator.getStorageDir(mStorageDirs, mUserId, 300);
    Assert.assertEquals(3, storageDir.getStorageDirId());
    createBlockFile(storageDir, BlockInfo.computeBlockId(1, 0), 300);
    storageDir = allocator.getStorageDir(mStorageDirs, mUserId, 300);
    Assert.assertEquals(2, storageDir.getStorageDirId());
    createBlockFile(storageDir, BlockInfo.computeBlockId(2, 0), 300);
    storageDir = allocator.getStorageDir(mStorageDirs, mUserId, 300);
    Assert.assertEquals(1, storageDir.getStorageDirId());
    createBlockFile(storageDir, BlockInfo.computeBlockId(3, 0), 300);
    storageDir = allocator.getStorageDir(mStorageDirs, mUserId, 1000);
    Assert.assertEquals(null, storageDir);
    boolean fitIn = allocator.fitInPossible(mStorageDirs, 1200);
    Assert.assertEquals(true, fitIn);
    mStorageDirs[2].lockBlock(BlockInfo.computeBlockId(1, 0), mUserId);
    fitIn = allocator.fitInPossible(mStorageDirs, 1200);
    Assert.assertEquals(false, fitIn);
  }

  @Test
  public void AllocateRoundRobinTest() throws IOException {
    AllocateStrategy allocator = 
        AllocateStrategies.getAllocateStrategy(AllocateStrategyType.ROUND_ROBIN);
    StorageDir storageDir = allocator.getStorageDir(mStorageDirs, mUserId, 300);
    Assert.assertEquals(1, storageDir.getStorageDirId());
    createBlockFile(storageDir, BlockInfo.computeBlockId(1, 0), 300);
    storageDir = allocator.getStorageDir(mStorageDirs, mUserId, 300);
    Assert.assertEquals(2, storageDir.getStorageDirId());
    createBlockFile(storageDir, BlockInfo.computeBlockId(2, 0), 300);
    storageDir = allocator.getStorageDir(mStorageDirs, mUserId, 300);
    Assert.assertEquals(3, storageDir.getStorageDirId());
    createBlockFile(storageDir, BlockInfo.computeBlockId(3, 0), 300);
    storageDir = allocator.getStorageDir(mStorageDirs, mUserId, 1000);
    Assert.assertEquals(null, storageDir);
    boolean fitIn = allocator.fitInPossible(mStorageDirs, 1200);
    Assert.assertEquals(true, fitIn);
    mStorageDirs[2].lockBlock(BlockInfo.computeBlockId(3, 0), mUserId);
    fitIn = allocator.fitInPossible(mStorageDirs, 1200);
    Assert.assertEquals(false, fitIn);
  }

  @Test
  public void AllocateRandomTest() throws IOException {
    AllocateStrategy allocator = 
        AllocateStrategies.getAllocateStrategy(AllocateStrategyType.RANDOM);
    StorageDir storageDir = allocator.getStorageDir(mStorageDirs, mUserId, 300);
    Assert.assertEquals(true, storageDir != null);
    createBlockFile(storageDir, BlockInfo.computeBlockId(1, 0), 300);
    storageDir = allocator.getStorageDir(mStorageDirs, mUserId, 300);
    Assert.assertEquals(true, storageDir != null);
    createBlockFile(storageDir, BlockInfo.computeBlockId(2, 0), 300);
    storageDir = allocator.getStorageDir(mStorageDirs, mUserId, 300);
    Assert.assertEquals(true, storageDir != null);
    createBlockFile(storageDir, BlockInfo.computeBlockId(3, 0), 300);
    storageDir = allocator.getStorageDir(mStorageDirs, mUserId, 1300);
    Assert.assertEquals(null, storageDir);
    boolean fitIn = allocator.fitInPossible(mStorageDirs, 1200);
    Assert.assertEquals(true, fitIn);
    fitIn = allocator.fitInPossible(mStorageDirs, 1300);
    Assert.assertEquals(false, fitIn);
  }

  private void initializeStorageDir(StorageDir dir, long userId) throws IOException {
    dir.initailize();
    UnderFileSystem ufs = dir.getUfs();
    ufs.mkdirs(dir.getUserTempPath(userId), true);
    CommonUtils.changeLocalFileToFullPermission(dir.getUserTempPath(userId));
  }
}
