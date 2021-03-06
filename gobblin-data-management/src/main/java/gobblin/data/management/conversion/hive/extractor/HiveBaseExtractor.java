package gobblin.data.management.conversion.hive.extractor;

import java.io.IOException;
import com.google.common.base.Optional;
import gobblin.configuration.WorkUnitState;
import gobblin.data.management.conversion.hive.source.HiveWorkUnit;
import gobblin.data.management.conversion.hive.watermarker.PartitionLevelWatermarker;
import gobblin.data.management.copy.hive.HiveDataset;
import gobblin.data.management.copy.hive.HiveDatasetFinder;
import gobblin.hive.HiveMetastoreClientPool;
import gobblin.source.extractor.Extractor;


/**
 * Base {@link Extractor} for extracting from {@link gobblin.data.management.conversion.hive.source.HiveSource}
 */
public abstract class HiveBaseExtractor<S, D> implements Extractor<S, D> {

  protected HiveWorkUnit hiveWorkUnit;
  protected HiveDataset hiveDataset;
  protected String dbName;
  protected String tableName;
  protected HiveMetastoreClientPool pool;

  public HiveBaseExtractor(WorkUnitState state) throws IOException {
    if (Boolean.valueOf(state.getPropAsBoolean(PartitionLevelWatermarker.IS_WATERMARK_WORKUNIT_KEY))) {
      return;
    }
    this.hiveWorkUnit = new HiveWorkUnit(state.getWorkunit());
    this.hiveDataset = hiveWorkUnit.getHiveDataset();
    this.dbName = hiveDataset.getDbAndTable().getDb();
    this.tableName = hiveDataset.getDbAndTable().getTable();
    this.pool = HiveMetastoreClientPool.get(state.getJobState().getProperties(),
        Optional.fromNullable(state.getJobState().getProp(HiveDatasetFinder.HIVE_METASTORE_URI_KEY)));
  }

  @Override
  public long getExpectedRecordCount() {
    return 1;
  }

  /**
   * Watermark is not managed by this extractor.
   */
  @Override
  public long getHighWatermark() {
    return 0;
  }

  @Override
  public void close() throws IOException {}
}
