package com.johnlpage.mews.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.model.UpdateStrategy;
import com.johnlpage.mews.repository.OptimizedMongoLoadRepository;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public abstract class MongoDbJsonLoaderService<T, ID> {

  private static final Logger LOG = LoggerFactory.getLogger(MongoDbJsonLoaderService.class);
  private final OptimizedMongoLoadRepository<T> repository;
  private final ObjectMapper objectMapper;
  private final JsonFactory jsonFactory;

  /** Parses a JSON stream object by object, assumes it's not an Array. */
  public void loadFromJsonStream(
      InputStream inputStream,
      Class<T> type,
      UpdateStrategy updateStrategy,
      PreWriteTriggerService<T> fuzzer) {


    AtomicInteger updates = new AtomicInteger(0);
    AtomicInteger deletes = new AtomicInteger(0);
    AtomicInteger inserts = new AtomicInteger(0);
    List<T> toSave = new ArrayList<>();

    int count = 0;
    long startTime = System.currentTimeMillis();
    try (BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
        JsonParser parser = jsonFactory.createParser(bufferedInputStream)) {
      // Iterate over tokens in the stream
      while (!parser.isClosed()) {
        // Check if the current token is the start of a new JSON object
        JsonToken token = parser.nextToken();
        if (token == JsonToken.START_OBJECT) {
          // Move the parser to the end of the current object
          JsonNode node = objectMapper.readTree(parser);

          T document = objectMapper.treeToValue(node,type);
          if (fuzzer != null) {
            // For a mutable model
            fuzzer.modifyMutableDataPreWrite(document);
            //for an immutable model
            //document = fuzzer.newImmutableDataPreWritedocument);
          }
          count++;
          toSave.add(document);
          if (toSave.size() >= 100) {
            // Alternative Options
            // repository.writeMany(toSave);
            // repository.saveAll(toSave);
            List<T> copyOfToSave = List.copyOf(toSave);
            toSave.clear();
            repository
                .asyncWriteMany(copyOfToSave, type, updateStrategy)
                .thenApply(
                    bulkWriteResult -> {
                      updates.addAndGet(bulkWriteResult.getModifiedCount());
                      deletes.addAndGet(bulkWriteResult.getDeletedCount());
                      inserts.addAndGet(bulkWriteResult.getUpserts().size());
                      return bulkWriteResult;
                    });
          }
        }
      }
      if (!toSave.isEmpty()) {
        // Alternative Options
        // repository.writeMany(toSave);
        // repository.saveAll(toSave);
        repository
            .asyncWriteMany(toSave, type, updateStrategy)
            .thenApply(
                bulkWriteResult -> {
                  updates.addAndGet(bulkWriteResult.getModifiedCount());
                  deletes.addAndGet(bulkWriteResult.getDeletedCount());
                  inserts.addAndGet(bulkWriteResult.getUpserts().size());
                  return bulkWriteResult;
                })
            .thenRun(toSave::clear);
      }
      final long endTime = System.currentTimeMillis();
      LOG.info("Processed {} docs. Time taken: {}ms.", count, endTime - startTime);
      LOG.info("Modified: {} Added: {} Removed: {}", updates, inserts, deletes);
    } catch (EOFException e) {
      LOG.error("Load Terminated as sender stopped sending JSON: {}", e.getMessage(), e);
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
    }
  }
}
