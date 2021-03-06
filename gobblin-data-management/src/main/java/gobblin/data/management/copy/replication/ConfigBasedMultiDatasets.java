package gobblin.data.management.copy.replication;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;

import com.google.common.base.Optional;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;

import gobblin.configuration.ConfigurationKeys;
import gobblin.data.management.copy.CopyConfiguration;
import lombok.extern.slf4j.Slf4j;


/**
 * Based on single dataset configuration in {@link Config} format, in Pull mode replication, there could be multiple 
 * {@link ConfigBasedDataset} generated. For example, if two replicas exists on the same copy to cluster, 
 * say replica1 and replica2, then there will be 2 {@link ConfigBasedDataset} generated, one for replication data from 
 * copy from {@link EndPoint} to replica1, the other from copy from {@link EndPoint} to replica2
 * 
 * This class will be responsible to generate those {@link ConfigBasedDataset}s
 * 
 * @author mitu
 */

@Slf4j
public class ConfigBasedMultiDatasets {

  private final Properties props;
  private final List<ConfigBasedDataset> datasets = new ArrayList<>();

  /**
   * if push mode is set in property, only replicate data when
   * 1. Push mode is set in Config store
   * 2. CopyTo cluster in sync with property with {@link #ConfigurationKeys.WRITER_FILE_SYSTEM_URI}
   */
  public static final String REPLICATION_PUSH_MODE = CopyConfiguration.COPY_PREFIX + ".replicationPushMode";

  public ConfigBasedMultiDatasets (Config c, Properties props){
    this.props = props;

    try {
      FileSystem executionCluster = FileSystem.get(new Configuration());
      URI executionClusterURI = executionCluster.getUri();

      ReplicationConfiguration rc = ReplicationConfiguration.buildFromConfig(c);
      
      // push mode
      if(this.props.containsKey(REPLICATION_PUSH_MODE) && Boolean.parseBoolean(this.props.getProperty(REPLICATION_PUSH_MODE))){
        generateDatasetInPushMode(rc, executionClusterURI);
      }
      // default pull mode
      else{
        generateDatasetInPullMode(rc, executionClusterURI);
      }
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
      log.error("Can not create Replication Configuration from raw config "
          + c.root().render(ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)), e);
    } catch (IOException ioe) {
      log.error("Can not decide current execution cluster ", ioe);

    }
  }

  private void generateDatasetInPushMode(ReplicationConfiguration rc, URI executionClusterURI){
    if(rc.getCopyMode()== ReplicationCopyMode.PULL){
      log.info("Skip process pull mode dataset with meta data{} as job level property specify push mode ", rc.getMetaData());
      return;
    }

    if (!this.props.containsKey(ConfigurationKeys.WRITER_FILE_SYSTEM_URI)){
      return;
    }
    String pushModeTargetCluster = this.props.getProperty(ConfigurationKeys.WRITER_FILE_SYSTEM_URI);
    
    // PUSH mode
    CopyRouteGenerator cpGen = rc.getCopyRouteGenerator();
    List<EndPoint> replicas = rc.getReplicas();
    List<EndPoint> pushCandidates = new ArrayList<EndPoint>(replicas);
    pushCandidates.add(rc.getSource());
    
    for(EndPoint pushFrom: pushCandidates){
      if(needGenerateCopyEntity(pushFrom, executionClusterURI)){
        Optional<List<CopyRoute>> copyRoutes = cpGen.getPushRoutes(rc, pushFrom);
        if(!copyRoutes.isPresent()) {
          log.warn("In Push mode, did not found any copyRoute for dataset with meta data {}", rc.getMetaData());
          return;
        }

        for(CopyRoute cr: copyRoutes.get()){
          if(cr.getCopyTo() instanceof HadoopFsEndPoint){

            HadoopFsEndPoint ep = (HadoopFsEndPoint)cr.getCopyTo();
            if(ep.getFsURI().toString().equals(pushModeTargetCluster)){
              this.datasets.add(new ConfigBasedDataset(rc, this.props, cr));
            }
          }
        }// inner for loops ends
      }
    }// outer for loop ends
  }

  private void generateDatasetInPullMode(ReplicationConfiguration rc, URI executionClusterURI){
    if(rc.getCopyMode()== ReplicationCopyMode.PUSH){
      log.info("Skip process push mode dataset with meta data{} as job level property specify pull mode ", rc.getMetaData());
      return;
    }

    // PULL mode
    CopyRouteGenerator cpGen = rc.getCopyRouteGenerator();
    List<EndPoint> replicas = rc.getReplicas();
    for(EndPoint replica: replicas){
      if(needGenerateCopyEntity(replica, executionClusterURI)){
        Optional<CopyRoute> copyRoute = cpGen.getPullRoute(rc, replica);
        if(copyRoute.isPresent()){
          this.datasets.add(new ConfigBasedDataset(rc, this.props, copyRoute.get()));
        }
      }
    }
  }

  public List<ConfigBasedDataset> getConfigBasedDatasetList(){
    return this.datasets;
  }

  private boolean needGenerateCopyEntity(EndPoint e, URI executionClusterURI){
    if(!(e instanceof HadoopFsEndPoint)){
      return false;
    }

    HadoopFsEndPoint ep = (HadoopFsEndPoint)e;
    return ep.getFsURI().equals(executionClusterURI);
  }
}
