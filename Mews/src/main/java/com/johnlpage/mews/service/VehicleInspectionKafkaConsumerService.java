package com.johnlpage.mews.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.model.UpdateStrategy;
import com.johnlpage.mews.model.VehicleInspection;
import com.johnlpage.mews.repository.optimized.OptimizedMongoLoadRepository;
import com.mongodb.bulk.BulkWriteResult;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/* This is unusual in that we want to read from the Topic but if we run out of
items on it then we want to send the batch we are working on , this code is simply to show how
a Kafka consumer can be used instead of a web service - it's the same code as the JSONLoader*/

@RequiredArgsConstructor
@Service
public class VehicleInspectionKafkaConsumerService {
  private static final org.slf4j.Logger LOG =
      LoggerFactory.getLogger(VehicleInspectionKafkaConsumerService.class);
  private final OptimizedMongoLoadRepository<VehicleInspection> repository;
  private final ObjectMapper objectMapper;
  private final JsonFactory jsonFactory;
  private final AtomicLong lastMessageTime = new AtomicLong(System.currentTimeMillis());
  List<VehicleInspection> toSave = new ArrayList<>();
  List<CompletableFuture<BulkWriteResult>> futures = new ArrayList<>();
  PostWriteTriggerService posttrigger = null;
  PreWriteTriggerService pretrigger = null;
  int processedCount = 0;
  UpdateStrategy updateStrategy = UpdateStrategy.REPLACE;
  AtomicInteger updates = new AtomicInteger(0);
  AtomicInteger deletes = new AtomicInteger(0);
  AtomicInteger inserts = new AtomicInteger(0);

  @KafkaListener(topics = "test", groupId = "my-group-id")
  public void listen(String message) {
    processedCount++;
    lastMessageTime.set(System.currentTimeMillis());
    if (processedCount % 10000 == 0) {
      LOG.info("KAFKA Read: {}", processedCount);
    }

    try {
      VehicleInspection document = objectMapper.readValue(message, VehicleInspection.class);
      if (pretrigger != null) {
        // For a mutable model
        pretrigger.modifyMutableDataPreWrite(document);
        // for an immutable model
        // document = pretrigger.newImmutableDataPreWritedocument);
      }
      toSave.add(document);
    } catch (Exception e) {
      // TODD - Handle Malformed JSON from KAFKA
    }
    if (toSave.size() >= 100) {
      sendBatch();
    }
    // System.out.println("Received message: " + message);
  }

  private void sendBatch() {
    List<VehicleInspection> copyOfToSave = List.copyOf(toSave);
    toSave.clear();
    CompletableFuture<BulkWriteResult> future =
        repository.asyncWriteMany(
            copyOfToSave, VehicleInspection.class, updateStrategy, posttrigger);

    futures.add(
        future.thenApply(
            bulkWriteResult -> {
              updates.addAndGet(bulkWriteResult.getModifiedCount());
              deletes.addAndGet(bulkWriteResult.getDeletedCount());
              inserts.addAndGet(bulkWriteResult.getUpserts().size());
              return bulkWriteResult;
            }));
  }

  @Scheduled(fixedRate = 1000) // Run every second
  public void checkForIdle() {
    long now = System.currentTimeMillis();
    long lastReceived = lastMessageTime.get();
    long idleTime = now - lastReceived;
    if (idleTime > 1000) { // No messages for 1 seconds
      sendBatch();
    }
  }

  @PreDestroy
  public void onShutdown() {
    sendBatch();
    System.out.println("Kafka Listener is shutting down.");
    CompletableFuture<Void> allFutures =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    // Wait for all futures to complete
    allFutures.join();
    LOG.info("Processed {} docs.", processedCount);
    LOG.info("Modified: {} Added: {} Removed: {}", updates, inserts, deletes);
  }
}
