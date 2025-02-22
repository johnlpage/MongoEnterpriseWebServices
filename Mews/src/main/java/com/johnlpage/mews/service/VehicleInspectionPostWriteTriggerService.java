package com.johnlpage.mews.service;

import com.johnlpage.mews.model.VehicleInspection;
import com.johnlpage.mews.model.VehicleInspectionHistory;
import com.johnlpage.mews.repository.optimized.OptimizedMongoLoadRepositoryImpl;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.ClientSession;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class VehicleInspectionPostWriteTriggerService
    implements PostWriteTriggerService<VehicleInspection> {

  private static final Logger LOG =
      LoggerFactory.getLogger(VehicleInspectionPostWriteTriggerService.class);

  private final MongoTemplate mongoTemplate;

  public VehicleInspectionPostWriteTriggerService(MongoTemplate mongoTemplate) {
    super();
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public void postWriteTrigger(
      ClientSession session,
      BulkWriteResult result,
      List<VehicleInspection> inspections,
      ObjectId updateId) {

    Query query = new Query();
    List<Long> testIdList = new ArrayList<>();
    for (VehicleInspection v : inspections) {
      testIdList.add(v.getTestid()); // This is easier to read than stream().map()
    }

    query.addCriteria(Criteria.where("_id").in(testIdList)); // testid is in the list
    query.addCriteria(Criteria.where(OptimizedMongoLoadRepositoryImpl.UPDATE_ID).is(updateId));
    query.fields().include(OptimizedMongoLoadRepositoryImpl.PREVIOUS_VALS);
    List<Document> modifiedOnly =
        mongoTemplate.withSession(session).find(query, Document.class, "inspections");

    // We want to take those and write them to another collection
    List<VehicleInspectionHistory> inspectionHistories = new ArrayList<>();
    for (Document v : modifiedOnly) {
      VehicleInspectionHistory vih = new VehicleInspectionHistory();
      vih.setTestid(v.getLong("_id"));
      vih.setChanges(v.get(OptimizedMongoLoadRepositoryImpl.PREVIOUS_VALS, Document.class));
      vih.setTimestamp(new Date());
      inspectionHistories.add(vih); // Add this history records to the history list
    }
    // Write them all in one operation
    mongoTemplate.withSession(session).insertAll(inspectionHistories);
  }
}
