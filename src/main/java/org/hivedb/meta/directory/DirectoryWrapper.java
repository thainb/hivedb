package org.hivedb.meta.directory;

import org.hivedb.*;
import org.hivedb.Lockable.Status;
import org.hivedb.meta.*;
import org.hivedb.util.HiveUtils;
import org.hivedb.util.Lists;
import org.hivedb.util.Preconditions;
import org.hivedb.util.functional.*;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

public class DirectoryWrapper implements DirectoryFacade {
	private Directory directory;
	private Assigner assigner;
	private Hive hive;
	
	public DirectoryWrapper(PartitionDimension dimension, DataSource dataSource, Assigner assigner, Hive hive) {
		this.directory = new Directory(dimension, dataSource);
		this.hive = hive;
		this.assigner = assigner;
	}
	
	public DirectoryWrapper(PartitionDimension dimension, Assigner assigner, Hive hive) {
		this.directory = new Directory(dimension);
		this.hive = hive;
		this.assigner = assigner;
	}

	public void deletePrimaryIndexKey(Object primaryIndexKey) throws HiveLockableException {
		if (!directory.doesPrimaryIndexKeyExist(primaryIndexKey))
			throw new HiveKeyNotFoundException("The primary index key " + primaryIndexKey
					+ " does not exist",primaryIndexKey);
		
		Preconditions.isWritable(directory.getKeySemamphoresOfPrimaryIndexKey(primaryIndexKey), hive);
		
		for (Resource resource : directory.getPartitionDimension().getResources()){
			if (!resource.isPartitioningResource())
				for(Object resourceId : directory.getResourceIdsOfPrimaryIndexKey(resource, primaryIndexKey)) {
					deleteResourceId(resource.getName(), resourceId);
				}
			else
				directory.batch().deleteAllSecondaryIndexKeysOfResourceId(resource, primaryIndexKey);
		}
		directory.deletePrimaryIndexKey(primaryIndexKey);
	}

	public void deleteResourceId(String resource, Object id) throws HiveLockableException {
		if (getResource(resource).isPartitioningResource())
			throw new HiveRuntimeException(String.format("Attempt to delete a resource id of resource %s, which is a partitioning dimension. It can only be deleted as a primary index key", id));
			
		Preconditions.isWritable(directory.getKeySemaphoresOfResourceId(getResource(resource), id), hive);
	
		directory.batch().deleteAllSecondaryIndexKeysOfResourceId(getResource(resource), id);
		directory.deleteResourceId(getResource(resource), id);
	}

	public void deleteSecondaryIndexKey(String resource, String secondaryIndex, Object secondaryIndexKey, Object resourceId) throws HiveLockableException {
		SecondaryIndex index = getSecondaryIndex(resource, secondaryIndex);
		Preconditions.isWritable(directory.getKeySemaphoresOfResourceId(getResource(resource), resourceId),hive);
		if (!directory.doesSecondaryIndexKeyExist(index, secondaryIndexKey, resourceId))
			throw new HiveKeyNotFoundException(
					String.format(
							"Secondary index key %s of secondary index %s does not exist",
							secondaryIndexKey,index.getName()),secondaryIndexKey);

		directory.deleteSecondaryIndexKey(index, secondaryIndexKey, resourceId);
	}

	public boolean doesPrimaryIndexKeyExist(Object primaryIndexKey) {
		return directory.doesPrimaryIndexKeyExist(primaryIndexKey);
	}

	public boolean doesResourceIdExist(String resource, Object resourceId) {
		return directory.doesResourceIdExist(getResource(resource), resourceId);
	}

	public boolean doesSecondaryIndexKeyExist(String resource, String secondaryIndex, Object secondaryIndexKey, Object resourceId) {
		return directory.doesSecondaryIndexKeyExist(getSecondaryIndex(resource, secondaryIndex), secondaryIndexKey, resourceId);
	}

	public Collection<KeySemaphoreImpl> getKeySemamphoresOfPrimaryIndexKey(Object primaryIndexKey) {
		return directory.getKeySemamphoresOfPrimaryIndexKey(primaryIndexKey);
	}

	public Collection<KeySemaphoreImpl> getKeySemaphoresOfResourceId(String resource, Object resourceId) {
		return directory.getKeySemaphoresOfResourceId(getResource(resource), resourceId);
	}

	public Collection<KeySemaphore> getKeySemaphoresOfSecondaryIndexKey(String resource, String secondaryIndex, Object secondaryIndexKey) {
		return directory.getKeySemaphoresOfSecondaryIndexKey(getSecondaryIndex(resource, secondaryIndex), secondaryIndexKey);
	}

	public Collection<Integer> getNodeIdsOfPrimaryIndexKey(Object primaryIndexKey) {
		return Transform.map(semaphoreToId(), directory.getKeySemamphoresOfPrimaryIndexKey(primaryIndexKey));
	}

	public Collection<Integer> getNodeIdsOfResourceId(String resource, Object id) {
		return Transform.map(semaphoreToId(), directory.getKeySemaphoresOfResourceId(getResource(resource), id));
	}

	public Collection<Integer> getNodeIdsOfSecondaryIndexKey(String resource, String secondaryIndex, Object secondaryIndexKey) {
		return Transform.map(semaphoreToId(), directory.getKeySemaphoresOfSecondaryIndexKey(getSecondaryIndex(resource, secondaryIndex), secondaryIndexKey));
	}

	public boolean getReadOnlyOfPrimaryIndexKey(Object primaryIndexKey) {
		Collection<Boolean> locks = 
			Transform.map(semaphoreToReadOnly(), directory.getKeySemamphoresOfPrimaryIndexKey(primaryIndexKey));
		Preconditions.isNotEmpty(locks, String.format("Unable to find partitionKey %s ", primaryIndexKey));
		return Lists.or(locks);
	}

	public boolean getReadOnlyOfResourceId(String resource, Object id) {
		Collection<Boolean> locks = 
			Transform.map(semaphoreToReadOnly(), directory.getKeySemaphoresOfResourceId(getResource(resource), id));
		Preconditions.isNotEmpty(locks, String.format("Unable to find resource %s with id = %s ", resource, id));
		return Lists.or(locks);
	}

	public Collection getResourceIdsOfPrimaryIndexKey(String resource, Object primaryIndexKey) {
		return directory.getResourceIdsOfPrimaryIndexKey(getResource(resource), primaryIndexKey);
	}

	public Integer insertPrimaryIndexKey(Object primaryIndexKey) throws HiveLockableException {
    Collection<Node> writableNodes = Filter.grep(new Predicate<Node>(){
      public boolean f(Node item) {
        return item.getStatus() == Lockable.Status.writable;
      }
    }, hive.getNodes());
    Node node = assigner.chooseNode(writableNodes, primaryIndexKey);
		Preconditions.isWritable(hive, node);
		directory.insertPrimaryIndexKey(node, primaryIndexKey);
    return node.getId();
	}

	public void insertResourceId(String resource, Object id, Object primaryIndexKey) throws HiveLockableException {
		if (getResource(resource).isPartitioningResource()) {
			insertPrimaryIndexKey(primaryIndexKey);
		} else {
			Collection<KeySemaphoreImpl> semaphores = directory.getKeySemamphoresOfPrimaryIndexKey(primaryIndexKey);
			Preconditions.isWritable(semaphores, hive);
			directory.insertResourceId(getResource(resource), id, primaryIndexKey);
		}
	}

	public void insertSecondaryIndexKey(String resource, String secondaryIndex, Object secondaryIndexKey, Object resourceId) throws HiveLockableException {
		Collection<KeySemaphoreImpl> semaphores =
			directory.getKeySemaphoresOfResourceId(getResource(resource), resourceId);
		Preconditions.isWritable(semaphores, hive);
		directory.insertSecondaryIndexKey(getSecondaryIndex(resource, secondaryIndex), secondaryIndexKey,resourceId);
	}

	public void updatePrimaryIndexKeyOfResourceId(String resource, Object resourceId, Object newPrimaryIndexKey) throws HiveLockableException {
		Preconditions.isWritable(directory.getKeySemamphoresOfPrimaryIndexKey(newPrimaryIndexKey), hive);
		final Resource r = getResource(resource);
		if (r.isPartitioningResource()) 
			throw new HiveRuntimeException(String.format("Resource %s is a partitioning dimension, you cannot update its primary index key because it is the resource id", r.getName()));
		
		directory.updatePrimaryIndexKeyOfResourceId(r, resourceId, newPrimaryIndexKey);
	}

	public void updatePrimaryIndexKeyReadOnly(Object primaryIndexKey, boolean isReadOnly) throws HiveLockableException {
		Collection<KeySemaphoreImpl> semaphores = directory.getKeySemamphoresOfPrimaryIndexKey(primaryIndexKey);
		Preconditions.isWritable(HiveUtils.getNodesForSemaphores(semaphores, hive));
		directory.updatePrimaryIndexKeyReadOnly(primaryIndexKey, isReadOnly);
	}
	
	private Resource getResource(String name) {
		return directory.getPartitionDimension().getResource(name);
	}
	
	private SecondaryIndex getSecondaryIndex(String resource, String name) {
		return directory.getPartitionDimension().getResource(resource).getSecondaryIndex(name);
	}
	
	public static Unary<KeySemaphore,Integer> semaphoreToId() {
		return new Unary<KeySemaphore, Integer>(){

			public Integer f(KeySemaphore item) {
				return item.getNodeId();
			}};
	}
	
	public static Unary<KeySemaphore, Boolean> semaphoreToReadOnly() {
		return new Unary<KeySemaphore, Boolean>(){

			public Boolean f(KeySemaphore item) {
				return item.getStatus().equals(Status.readOnly);
			}};
	}

	public Object getPrimaryIndexKeyOfResourceId(String name, Object resourceId) {
		return getResource(name).isPartitioningResource()
			? resourceId
			: directory.getPrimaryIndexKeyOfResourceId(getResource(name), resourceId);
	}

	public Collection getPrimaryIndexKeysOfSecondaryIndexKey(String resource, String secondaryIndex, Object secondaryIndexKey) {
		return directory.getPrimaryIndexKeysOfSecondaryIndexKey(getSecondaryIndex(resource, secondaryIndex), secondaryIndexKey);
	}

	public Collection getResourceIdsOfSecondaryIndexKey(String resource, String secondaryIndex, Object secondaryIndexKey) {
		return directory.getResourceIdsOfSecondaryIndexKey(getSecondaryIndex(resource, secondaryIndex), secondaryIndexKey);
	}

	public Collection getSecondaryIndexKeysWithResourceId(String resource, String secondaryIndex, Object id) {
		return directory.getSecondaryIndexKeysOfResourceId(getSecondaryIndex(resource, secondaryIndex), id);
	}

	public void deleteAllSecondaryIndexKeysOfResourceId(String resource,Object id) throws HiveLockableException{
		Preconditions.isWritable(directory.getKeySemaphoresOfResourceId(getResource(resource), id), hive);
		directory.batch().deleteAllSecondaryIndexKeysOfResourceId(getResource(resource), id);
	}

	public void deleteSecondaryIndexKeys(final String resource, Map<String, Collection<Object>> secondaryIndexValueMap, Object resourceId) throws HiveLockableException {
		Preconditions.isWritable(directory.getKeySemaphoresOfResourceId(getResource(resource), resourceId), hive);
		directory.batch().deleteSecondaryIndexKeys(stringMapToIndexValueMap(resource, secondaryIndexValueMap), resourceId);
	}

	public void insertSecondaryIndexKeys(String resource, Map<String, Collection<Object>> secondaryIndexValueMap, Object resourceId) throws HiveLockableException {
		Preconditions.isWritable(directory.getKeySemaphoresOfResourceId(getResource(resource), resourceId), hive);
		directory.batch().insertSecondaryIndexKeys(stringMapToIndexValueMap(resource, secondaryIndexValueMap), resourceId);
	}
	
	private Map<SecondaryIndex, Collection<Object>> stringMapToIndexValueMap(final String resource, final Map<String, Collection<Object>> map) {
		return Transform.toMap(
				Transform.map(
						new Unary<Entry<String, Collection<Object>>, Entry<SecondaryIndex, Collection<Object>>>(){
							public Entry<SecondaryIndex, Collection<Object>> f(Entry<String, Collection<Object>> item) {
								return new Pair<SecondaryIndex, Collection<Object>>(
										getSecondaryIndex(resource, item.getKey()), 
										item.getValue());
							}
						}, map.entrySet()));
	}
}
