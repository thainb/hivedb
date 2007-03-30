package org.hivedb.management.statistics;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;

import org.hivedb.HiveException;
import org.hivedb.meta.AccessType;
import org.hivedb.meta.Hive;
import org.hivedb.meta.IndexSchema;
import org.hivedb.persistence.DaoTestCase;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TestHivePerformanceStatistics extends DaoTestCase{
	private Hive hive;
	
	@BeforeClass
	public void setup() throws Exception {
		hive = Hive.load(getConnectString());
		hive.addPartitionDimension(createPopulatedPartitionDimension());
		new IndexSchema(hive.getPartitionDimension(partitionDimensionName())).install();
		hive.addNode(hive.getPartitionDimension(partitionDimensionName()), createNode());
		hive.insertPrimaryIndexKey(partitionDimensionName(), intKey());
	}
	
	@AfterMethod
	public void resetReadOnly() throws HiveException {
		hive.updateHiveReadOnly(false);
	}
	
	@Test
	public void testWriteConnectionTracking() throws Exception{
		Collection<Connection> connections = new ArrayList<Connection>();
		
		for(int i=0; i<5; i++)
			connections.add( hive.getConnection( hive.getPartitionDimension(partitionDimensionName()), intKey()));
		
		Assert.assertEquals(connections.size(), hive.stats.getSumNewWriteConnections());
	}
	
	@Test
	public void testReadConnectionTracking() throws Exception{
		Collection<Connection> connections = new ArrayList<Connection>();
		for(int i=0; i<5; i++)
			connections.add( hive.getConnection( hive.getPartitionDimension(partitionDimensionName()), intKey(), AccessType.Read));
		
		Assert.assertEquals(connections.size(), hive.stats.getSumNewReadConnections());
	}
	
	@Test
	public void testConnectionFailures() throws Exception {
		hive.updateHiveReadOnly(true);
		Collection<Connection> connections = new ArrayList<Connection>();
		for(int i=0; i<5; i++){
			try {
				connections.add( hive.getConnection( hive.getPartitionDimension(partitionDimensionName()), intKey(), AccessType.ReadWrite));
			} catch( Exception e) {
				//CRUSH! KILL! DESTROY!
			}
		}
		
		Assert.assertEquals(5, hive.stats.getSumConnectionFailures());
	}
	
	public Integer intKey() { return new Integer(7); }
}