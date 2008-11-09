/**
 * 
 */
package org.hivedb.persistence;

import org.hivedb.Node;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;

public class HiveBasicDataSourceProvider implements HiveDataSourceProvider {
	private Long connectionTimeoutInMillis = 0l;
	private Long socketTimeoutInMillis = 0l;
	
	public HiveBasicDataSourceProvider(long connection, long socket) {
		this.connectionTimeoutInMillis = connection;
		this.socketTimeoutInMillis = socket;
	}
	
	public HiveBasicDataSourceProvider(long timeout) {
		this(timeout,timeout);
	}
	
	public DataSource getDataSource(Node node) {
		return getDataSource(node.getUri());
	}

	public long getTimeout() {
		return connectionTimeoutInMillis;
	}

	public void setTimeout(long timeout) {
		this.connectionTimeoutInMillis = timeout;
	}

	public DataSource getDataSource(String uri) {
		HiveBasicDataSource ds = new HiveBasicDataSource(uri);
		return new LazyConnectionDataSourceProxy(ds);
	}

	public Long getSocketTimeout() {
		return socketTimeoutInMillis;
	}

	public void setSocketTimeout(Long socketTimeoutInMillis) {
		this.socketTimeoutInMillis = socketTimeoutInMillis;
	}
}