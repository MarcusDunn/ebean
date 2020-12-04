package io.ebeaninternal.server.loadcontext;

import io.ebean.FetchConfig;
import io.ebean.bean.ObjectGraphNode;
import io.ebean.bean.PersistenceContext;
import io.ebeaninternal.api.SpiQuery;
import io.ebeaninternal.server.deploy.BeanDescriptor;
import io.ebeaninternal.server.querydefn.OrmQueryProperties;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Base class for Bean and BeanCollection loading (lazy loading and query join loading).
 */
abstract class DLoadBaseContext {

  protected final ReentrantLock lock = new ReentrantLock();

  protected final DLoadContext parent;

  protected final BeanDescriptor<?> desc;

  protected final String fullPath;

  protected final String serverName;

  final OrmQueryProperties queryProps;

  final boolean hitCache;

  final int firstBatchSize;

  final int secondaryBatchSize;

  final ObjectGraphNode objectGraphNode;

  final boolean queryFetch;

  DLoadBaseContext(DLoadContext parent, BeanDescriptor<?> desc, String path, int defaultBatchSize, OrmQueryProperties queryProps) {
    this.parent = parent;
    this.serverName = parent.getEbeanServer().getName();
    this.desc = desc;
    this.queryProps = queryProps;
    this.fullPath = parent.getFullPath(path);
    this.hitCache = parent.isBeanCacheGet() && desc.isBeanCaching();
    this.objectGraphNode = parent.getObjectGraphNode(path);
    this.queryFetch = queryProps != null && queryProps.isQueryFetch();
    this.firstBatchSize = initFirstBatchSize(defaultBatchSize, queryProps);
    this.secondaryBatchSize = initSecondaryBatchSize(defaultBatchSize, firstBatchSize, queryProps);
  }

  private int initFirstBatchSize(int batchSize, OrmQueryProperties queryProps) {
    if (queryProps == null) {
      return batchSize;
    }

    int queryBatchSize = queryProps.getQueryFetchBatch();
    if (queryBatchSize == -1) {
      return batchSize;

    } else if (queryBatchSize == 0) {
      return 100;

    } else {
      return queryBatchSize;
    }
  }

  private int initSecondaryBatchSize(int defaultBatchSize, int firstBatchSize, OrmQueryProperties queryProps) {
    if (queryProps == null) {
      return defaultBatchSize;
    }
    FetchConfig fetchConfig = queryProps.getFetchConfig();
    if (fetchConfig.isQueryAll()) {
      return firstBatchSize;
    }

    int lazyBatchSize = fetchConfig.getLazyBatchSize();
    return (lazyBatchSize > 1) ? lazyBatchSize : defaultBatchSize;
  }

  /**
   * If the parent has a query plan label then extend it with the path and
   * set onto the secondary query.
   */
  void setLabel(SpiQuery<?> query) {

    String label = parent.getPlanLabel();
    if (label != null) {
      query.setProfilePath(label, fullPath, parent.getProfileLocation());
    }
  }

  PersistenceContext getPersistenceContext() {
    return parent.getPersistenceContext();
  }

}
