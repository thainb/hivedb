package org.hivedb.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.EnumSet;

import org.hivedb.annotations.IndexType;
import org.hivedb.configuration.EntityConfig;
import org.hivedb.configuration.EntityHiveConfig;
import org.hivedb.configuration.EntityIndexConfig;
import org.hivedb.hibernate.DataAccessObject;
import org.hivedb.meta.EntityGenerator;
import org.hivedb.meta.EntityGeneratorImpl;
import org.hivedb.util.functional.Delay;
import org.hivedb.util.functional.Filter;
import org.hivedb.util.functional.Generate;
import org.hivedb.util.functional.Generator;
import org.hivedb.util.functional.NumberIterator;
import org.hivedb.util.functional.Predicate;
import org.hivedb.util.functional.RingIteratorable;
import org.hivedb.util.functional.Transform;
import org.hivedb.util.functional.Unary;

public class GenerateHiveIndexKeys {
	
	private DataAccessObject<Object, Serializable> dataAccessObject;
	private int primaryInstanceCount;
	private int resourceInstanceCount;
	public GenerateHiveIndexKeys(DataAccessObject<Object, Serializable> dataAccessObject, int primaryIndexInstanceCount, int resourceInstanceCount)
	{
		this.dataAccessObject = dataAccessObject;
		this.primaryInstanceCount = primaryIndexInstanceCount;
		this.resourceInstanceCount = resourceInstanceCount;
	}
	
	@SuppressWarnings("unchecked")
	public Collection<Object> createResourceInstances(
		final EntityHiveConfig entityHiveConfig, final Class representedInterface)
	{
		final EntityConfig entityConfig =  entityHiveConfig.getEntityConfig(representedInterface);
		if (entityConfig.isPartitioningResource()
			&& primaryInstanceCount != resourceInstanceCount)
			throw new RuntimeException("For partitioning resources, configure the primaryInstanceCount and resourceInstanceCount equally");
		final Collection<Object> partitionDimensionIds = createPartitionDimensionIds(entityHiveConfig.getEntityConfig(representedInterface));
		final EntityGenerator<Object> entityConfigGenerator =
				new EntityGeneratorImpl<Object>(entityConfig);
		
		final Iterable<Object> primaryIndexKeysIterable = new RingIteratorable<Object>(
			partitionDimensionIds,
			resourceInstanceCount);
		
		Collection<Object> resourceInstances = Transform.map(
			new Unary<Object, Object>() {
				public Object f(Object primaryIndexKey) {
					return entityConfigGenerator.generate(primaryIndexKey);
			}},
			primaryIndexKeysIterable);	
		
		for (Object resourceInstance : resourceInstances) {
			// reassign to the result. The returned instance need not be that passed in.
			resourceInstance = dataAccessObject.save(resourceInstance);
		}
		
		return resourceInstances;
	}
	private static Collection<? extends EntityIndexConfig> getHiveIndexes(final EntityConfig entityConfig) {
		return Filter.grep(new Predicate<EntityIndexConfig>() {
			public boolean f(EntityIndexConfig entityIndexConfig) {
				return entityIndexConfig.getIndexType().equals(IndexType.Hive);	
			}}, entityConfig.getEntityIndexConfigs());
	}
	

	static QuickCache primitiveGenerators = new QuickCache(); // cache generators for sequential randomness
	/**
	 * @param entityConfig
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Collection<Object> createPartitionDimensionIds(final EntityConfig entityConfig)
	{	
		final Generator primaryIndexIdentifiableGenerator = 
			primitiveGenerators.get(entityConfig.getPrimaryIndexKeyPropertyName(), new Delay<GeneratePrimitiveValue>() {
				public GeneratePrimitiveValue f() {
					return new GeneratePrimitiveValue(ReflectionTools.getPropertyType(
							entityConfig.getRepresentedInterface(),
							entityConfig.getPrimaryIndexKeyPropertyName()));
				}});
		
		return Generate.create(new Generator<Object>() { 
			public Object generate() {
				try {
					return primaryIndexIdentifiableGenerator.generate();
				} catch (Exception e) { throw new RuntimeException(e); }
			}},
			new NumberIterator(primaryInstanceCount));
	}	
}
