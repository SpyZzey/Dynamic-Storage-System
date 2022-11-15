package de.storagesystem.api.storage.buckets;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

public interface BucketDAO extends CrudRepository<Bucket, Long>, BucketCustomDAO {
}