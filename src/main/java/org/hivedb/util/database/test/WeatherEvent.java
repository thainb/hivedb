package org.hivedb.util.database.test;

import org.hivedb.annotations.EntityId;
import org.hivedb.annotations.GeneratedClass;
import org.hivedb.annotations.Index;

import java.io.Serializable;
import java.util.Collection;


@GeneratedClass("WeatherEventGenerated")
public interface WeatherEvent extends Serializable {
	@Index
	@EntityId
	Integer getEventId();
	String getName();
	Collection<Integer> getStatistics();
}
