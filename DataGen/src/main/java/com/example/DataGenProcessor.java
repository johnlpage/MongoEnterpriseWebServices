package com.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class DataGenProcessor {
    private final Map<String, List<CSVRecord>> csvData = new HashMap<>();
    private final Map<String, List<String>> fieldNames = new HashMap<>();
    private final Map<String, TreeSet<CSVLine>> csvTrees = new HashMap<>();
    private final Map<String, Integer> maxProbability = new HashMap<>();
    ValueMaker valueMaker;
    ObjectMapper objectMapper;
    Random random;

    DataGenProcessor(String directoryPath) throws IOException {
        readCsvFiles(directoryPath);
        random = new Random(0);
        valueMaker= new ValueMaker(random,directoryPath);
        objectMapper = new ObjectMapper();
        buildLookupTree();
    }

    List<ObjectNode> generateJsonDocuments(
            int numberOfJsonDocuments) throws IOException {
        List<ObjectNode> rval = new ArrayList<>();



        for (int i = 0; i < numberOfJsonDocuments; i++) {
            ObjectNode jsonNode = objectMapper.createObjectNode();

            for (Map.Entry<String, Integer> entry : maxProbability.entrySet()) {
                int totalProbability = entry.getValue();
                int randomValue = (int) Math.floor(random.nextDouble() * totalProbability);
                TreeSet<CSVLine> csvTree = csvTrees.get(entry.getKey());
                CSVLine chosen = csvTree.higher(new CSVLine(randomValue, null));

                CSVRecord record = chosen.getCsvRecord();
                for (String field : fieldNames.get(entry.getKey())) {
                    if (!field.equals("probability")) {
                        //TODO - Add non literal value handling
                        Object value ;
                        String asString = record.get(field);
                        if(asString.startsWith("@")) {
                            value = valueMaker.expandValue(asString);
                        } else {
                            value = asString;
                        }
                        // Nested values
                        if(field.contains(".")) {
                            String[] parts = field.split("\\.");
                            ObjectNode here = jsonNode;
                            int depth = 0;
                            for(String part: parts) {
                                depth++;
                                if(depth < parts.length) {
                                    here.putIfAbsent(part, objectMapper.createObjectNode());
                                    here = (ObjectNode) here.get(part);
                                } else {

                                    setNode(here,part, value);
                                }
                            }
                         } else {
                            setNode(jsonNode,field, value);
                        }
                    }
                }
            }
            rval.add(jsonNode);
          /*  System.out.println(
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode));*/
        }
        return rval;
    }

    private void setNode(ObjectNode where, String key, Object value) {
        if(value instanceof ObjectNode) {
            // if the key is "ROOT" then replace don't add
            if(key.equals("ROOT")) {
                where.removeAll();
                where.setAll((ObjectNode) value);
            } else {
                where.put(key, (ObjectNode) value);
            }

        } else
    if(value instanceof List ) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        // Add all ObjectNodes from the list to the ArrayNode
        for (ObjectNode objectNode : (List<ObjectNode>) value) {
            arrayNode.add(objectNode);
        }

        where.put(key,arrayNode);
    } else
        if (value instanceof Double) {
            where.put(key,(Double) value);
        } else if (value instanceof Long) {
            where.put(key,(Long) value);
        }
        else if (value instanceof Integer) {
            where.put(key,(Integer) value);
        } else if (value instanceof Boolean) {
            where.put(key,(Boolean) value);
        } else if (value instanceof LocalDate) {
            LocalDate ld = (LocalDate) value;
            where.put(key, ld.format(DateTimeFormatter.ISO_DATE));
        } else if (value instanceof LocalDateTime) {
            LocalDateTime ld = (LocalDateTime) value;
            where.put(key, ld.format(DateTimeFormatter.ISO_DATE));
        }
        else  if(value instanceof String) {
            try {
                where.put(key, Long.parseLong((String) value));
            }
            catch (NumberFormatException e) {
                try {
                    // the CSV parser considers everything as strings but in JS I'd like some to be numbers
                    where.put(key, Double.parseDouble((String) value));
                } catch (NumberFormatException e2) {

                    if (value.equals("true") || value.equals("false")) {
                        where.put(key, Boolean.parseBoolean((String) value));
                    } else {
                        if(value.equals("") || value.equals("null")) {
                            //TO NOT include null values
                        } else {
                            where.put(key, (String) value);
                        }
                    }
                }
            }
        }
    }

    // Read the CSV Files into a has of Lists
     void readCsvFiles(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        File[] files = directory.listFiles();
        for (File file : files) {
            if(file.isFile() && file.getName().endsWith(".gz")) {
                String filename = file.getName();

                //System.out.println("Processing " + filename);
                FileInputStream fileInputStream = new FileInputStream(file);
                GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
                InputStreamReader inputStreamReader = new InputStreamReader(gzipInputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                CSVParser parser =
                        new CSVParser(
                                bufferedReader,
                                CSVFormat.DEFAULT.withFirstRecordAsHeader());
                List<CSVRecord> records = parser.getRecords();

                if (records.size() > 0) {
                    csvData.put(filename, records);
                }
                fieldNames.put(filename, parser.getHeaderNames());

            }
        }
    }

    // For each CSV Files, compute the total of the probability column and also
    // A cumulative value we can use to find a specific element, for this we use a TreeSet
    // Which is a Red/Black tree that's best to find things in when using < and >

     void buildLookupTree() {
        for (Map.Entry<String, List<CSVRecord>> entry : csvData.entrySet()) {
            String fName = entry.getKey();
            List<CSVRecord> records = entry.getValue();
            TreeSet<CSVLine> lineSet = new TreeSet<CSVLine>(Comparator.comparingInt(CSVLine::getCumulativeProbability));
            int cumulativeProbability = 0;
            for (CSVRecord record : records) {
                int probability = (int) Double.parseDouble(record.get("probability"));
                cumulativeProbability += probability;
                CSVLine line = new CSVLine(cumulativeProbability, record);
                lineSet.add(line);
            }
            csvTrees.put(fName, lineSet);
            maxProbability.put(fName, cumulativeProbability);
        }
    }

}
