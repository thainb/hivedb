package org.hivedb.util.database.test;


import org.hivedb.annotations.*;

import java.util.Collection;
import java.util.Date;

/**
 *
 * Example entity class. Demonstrates an entity class containing all of the different types of indexes.s
 */
@GeneratedClass("WeatherReportGenerated")
@Resource("WeatherReport")
public interface WeatherReport {
	
	public static final String REPORT_ID = "reportId";
	public static final String TEMPERATURE = "temperature";
	public static final String CONTINENT = "continent";
    
	/**
	 *  The partition index of this entity. The Hive creates a partition dimension index that maps
	 *  each value to the node upon which to find all data associated with this value.
	 * @return
	 */
    @PartitionIndex
	String getContinent();
  void setContinent(String contintent);
    /**
     *  The Id. of the entity
     * @return
     */
    @EntityId
	Integer getReportId();
  void setReportId(Integer reportId);

    /**
     *  Demonstrates a Hive index. The Hive creates an index for this property allowing the Hive
     *  to resolve the node that contains the WeatherReports with this regionCode. This property
     *  is also a data index (see below.)
     *  
     * @return
     */
    @Index
    Integer getRegionCode();
  void setRegionCode(Integer reportId);

    /**
     *  Demonstrates a data index. A data index provides a hint to the data access object implementation
     *  that this property is an index on the data table appropriate for querying. All other indexes
     *  are assumed to be data indexes.
     * @return
     */
    @Index(type=IndexType.Data)
	Double getLatitude();
  void setLatitude(Double reportId);

    /**
     *  Demonstrates an unindexed property. Although this property may be represented by a column in the data table
     *  (depending on its Hibernate mapping) there is no hint to indicate that it can be queried upon.
     * @return
     */
    Double getLongitude();
  void setLongitude(Double reportId);

	/**
	 *  A property that delegates to another entity. The Hive has the values of temperature indexed
	 *  under the Temperate entity, so there is no need to store a duplicate Hive index. When
	 *  node-resolving based on this property, the code will use the Temperature entity to resolve
	 *  the node.
	 * @return
	 */
	@Index(type=IndexType.Delegates)
	@IndexDelegate("org.hivedb.util.database.test.Temperature")
	int getTemperature();
  void setTemperature(int reportId);

	/**
	 *  Demonstrates a Hive index on a collection property of primitive values. Each value of the collection
	 *  will be added to the Hive index so that any value can be used to resolve to the node upon which
	 *  this WeatherReport exists. Note that this example assumes that sources operate only on one continent,
	 *  otherwise a source could resolve to multiple nodes.
	 * @return
	 */
	@Index
	Collection<Integer> getSources();
  void setSources(Collection<Integer> reportId);

	/** 
	 *  Demonstrates a Hive index on a collection property of complex values. The class contained in the
	 *  collection must define an @Index attribute on a primitive property to tell the Hive what to index.
	 * 
	 * @return
	 */
	@Index
	Collection<WeatherEvent> getWeatherEvents();
  void setWeatherEvents(Collection<WeatherEvent> reportId);  

	@Validate("org.hivedb.util.validators.NoValidator")
	Date getReportTime();
  void setReportTime(Date reportId);  

}
