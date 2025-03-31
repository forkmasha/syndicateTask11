package com.task11;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.*;

public class DatabaseService {
    private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
    private final DynamoDB dynamoDB = new DynamoDB(client);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String TABLES_TABLE_NAME = System.getenv("tables_table");
    private final String RESERVATIONS_TABLE_NAME = System.getenv("reservations_table");

    public APIGatewayProxyResponseEvent createTable(String body) {
        try {
            if (TABLES_TABLE_NAME == null || TABLES_TABLE_NAME.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(500)
                        .withBody("{\"message\": \"Server configuration error: Table name is missing\"}");
            }

            Table table = dynamoDB.getTable(TABLES_TABLE_NAME);
            Map<String, Object> requestMap = objectMapper.readValue(body, HashMap.class);

            if (!requestMap.containsKey("id")) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("{\"message\": \"Missing 'id' field in request body\"}");
            }

            String tableId = String.valueOf(requestMap.get("id"));

            Item item = new Item()
                    .withPrimaryKey("id", tableId)  // Use String ID
                    .withInt("number", (Integer) requestMap.get("number"))
                    .withInt("places", (Integer) requestMap.get("places"))
                    .withBoolean("isVip", (Boolean) requestMap.get("isVip"));

            if (requestMap.containsKey("minOrder")) {
                item.withInt("minOrder", (Integer) requestMap.get("minOrder"));
            }

            table.putItem(item);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(objectMapper.writeValueAsString(Map.of("id", Integer.parseInt(tableId))));


        } catch (Exception e) {
            System.err.println("ERROR: Failed to create table - " + e.getMessage());
            e.printStackTrace();
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"message\": \"Bad Request\"}");
        }
    }


    public APIGatewayProxyResponseEvent createReservation(String body) {
        try {
            if (RESERVATIONS_TABLE_NAME == null || RESERVATIONS_TABLE_NAME.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(500)
                        .withBody("{\"message\": \"Server configuration error: Reservations table name is missing\"}");
            }

            Table reservationTable = dynamoDB.getTable(RESERVATIONS_TABLE_NAME);
            Table tablesTable = dynamoDB.getTable(TABLES_TABLE_NAME);
            Map<String, Object> requestMap = objectMapper.readValue(body, HashMap.class);


            String tableId = requestMap.containsKey("tableId") ? requestMap.get("tableId").toString() :
                    requestMap.containsKey("tableNumber") ? requestMap.get("tableNumber").toString() : null;

            if (tableId == null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("{\"message\": \"Missing 'tableId' field in request body\"}");
            }


            String customerName = requestMap.getOrDefault("customerName", requestMap.get("clientName")).toString();
            String customerEmail = requestMap.getOrDefault("customerEmail", requestMap.get("phoneNumber")).toString();
            String reservationDate = requestMap.getOrDefault("reservationDate", requestMap.get("date")).toString();
            String slotStart = requestMap.getOrDefault("slotTimeStart", "").toString();
            String slotEnd = requestMap.getOrDefault("slotTimeEnd", "").toString();


            if (customerName == null || customerEmail == null || reservationDate == null || slotStart.isEmpty() || slotEnd.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("{\"message\": \"Missing required fields\"}");
            }


            BigDecimal tableNumber;
            try {
                tableNumber = new BigDecimal(tableId);
            } catch (NumberFormatException e) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("{\"message\": \"Invalid table number format\"}");
            }


            if (!isTableExist(tableNumber)) {
                System.out.println("Validation failed < Table does not exist >");
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("{\"message\": \"Table does not exist\"}");
            }


            ScanSpec scanSpec = new ScanSpec();
            ItemCollection<ScanOutcome> reservations = reservationTable.scan(scanSpec);

            for (Item existing : reservations) {
                if (existing.getString("tableId").equals(tableId) && existing.getString("reservationDate").equals(reservationDate)) {
                    String existingStart = existing.getString("slotTimeStart");
                    String existingEnd = existing.getString("slotTimeEnd");

                    if (isTimeOverlap(existingStart, existingEnd, slotStart, slotEnd)) {
                        return new APIGatewayProxyResponseEvent()
                                .withStatusCode(400)
                                .withBody("{\"message\": \"Time slot overlaps with existing reservation\"}");
                    }
                }
            }


            String reservationId = UUID.randomUUID().toString();
            Item item = new Item()
                    .withPrimaryKey("id", reservationId)
                    .withString("tableId", tableId)  // Ensure tableId is stored as a String
                    .withString("customerName", customerName)
                    .withString("customerEmail", customerEmail)
                    .withString("reservationDate", reservationDate)
                    .withString("slotTimeStart", slotStart)
                    .withString("slotTimeEnd", slotEnd);

            reservationTable.putItem(item);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(objectMapper.writeValueAsString(Map.of("reservationId", reservationId)));

        } catch (Exception e) {
            e.printStackTrace();
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"message\": \"Failed to create reservation: " + e.getMessage() + "\"}");
        }
    }

    private boolean isTimeOverlap(String existingStart, String existingEnd, String newStart, String newEnd) {
        return (newStart.compareTo(existingEnd) < 0 && newEnd.compareTo(existingStart) > 0);
    }

    public APIGatewayProxyResponseEvent getTables() {
        try {
            if (TABLES_TABLE_NAME == null || TABLES_TABLE_NAME.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(500)
                        .withBody("{\"message\": \"Server configuration error: Table name is missing\"}");
            }

            Table table = dynamoDB.getTable(TABLES_TABLE_NAME);
            ScanSpec scanSpec = new ScanSpec();
            ItemCollection<ScanOutcome> items = table.scan(scanSpec);

            List<Map<String, Object>> tablesList = new ArrayList<>();
            for (Item item : items) {
                Map<String, Object> tableData = new HashMap<>();
                tableData.put("id", Integer.parseInt(item.getString("id")));  // Convert back to Integer
                tableData.put("number", item.getInt("number"));
                tableData.put("places", item.getInt("places"));
                tableData.put("isVip", item.getBoolean("isVip"));
                tableData.put("minOrder", item.hasAttribute("minOrder") ? item.getInt("minOrder") : null);
                tablesList.add(tableData);
            }

            String jsonResponse = objectMapper.writeValueAsString(Map.of("tables", tablesList));
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(jsonResponse);
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"message\": \"Failed to fetch tables\"}");
        }
    }

    public APIGatewayProxyResponseEvent getReservations() {
        try {
            if (RESERVATIONS_TABLE_NAME == null || RESERVATIONS_TABLE_NAME.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(500)
                        .withBody("{\"message\": \"Server configuration error: Reservations table name is missing\"}");
            }

            Table table = dynamoDB.getTable(RESERVATIONS_TABLE_NAME);
            ScanSpec scanSpec = new ScanSpec();
            ItemCollection<ScanOutcome> items = table.scan(scanSpec);

            List<Map<String, Object>> reservationsList = new ArrayList<>();
            for (Item item : items) {
                Map<String, Object> reservationData = new HashMap<>();
                reservationData.put("tableNumber", Integer.parseInt(item.getString("tableId")));  // Convert tableId to Integer
                reservationData.put("clientName", item.getString("customerName"));
                reservationData.put("phoneNumber", item.getString("customerEmail"));
                reservationData.put("date", item.getString("reservationDate"));

                if (item.hasAttribute("slotTimeStart")) {
                    reservationData.put("slotTimeStart", item.getString("slotTimeStart"));
                }
                if (item.hasAttribute("slotTimeEnd")) {
                    reservationData.put("slotTimeEnd", item.getString("slotTimeEnd"));
                }

                reservationsList.add(reservationData);
            }

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(objectMapper.writeValueAsString(Map.of("reservations", reservationsList)));

        } catch (Exception e) {
            e.printStackTrace();
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"message\": \"Failed to fetch reservations\"}");
        }
    }

    public APIGatewayProxyResponseEvent getTableById(String id) {
        try {
            if (TABLES_TABLE_NAME == null || TABLES_TABLE_NAME.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(500)
                        .withBody("{\"message\": \"Server configuration error: Table name is missing\"}");
            }

            Table table = dynamoDB.getTable(TABLES_TABLE_NAME);
            Item item = table.getItem("id", id);

            if (item == null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(404)
                        .withBody("{\"message\": \"Table not found\"}");
            }

            Map<String, Object> tableData = new HashMap<>();
            tableData.put("id", Integer.parseInt(item.getString("id")));
            tableData.put("number", item.getInt("number"));
            tableData.put("places", item.getInt("places"));
            tableData.put("isVip", item.getBoolean("isVip"));
            tableData.put("minOrder", item.hasAttribute("minOrder") ? item.getInt("minOrder") : null);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(objectMapper.writeValueAsString(tableData));

        } catch (Exception e) {
            e.printStackTrace();
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"message\": \"Failed to fetch table\"}");
        }
    }
    private boolean isTableExist(BigDecimal tableNumber) {
        System.out.println("isTableExist < Tables > ");
        Table table = dynamoDB.getTable(TABLES_TABLE_NAME);
        ScanSpec scanSpec = new ScanSpec();

        ItemCollection<ScanOutcome> items = table.scan(scanSpec);
        boolean isTableExist = false;
        Iterator<Item> iterator = items.iterator();
        while (iterator.hasNext()) {
            Item item = iterator.next();
            BigDecimal currTableNumber = item.getNumber("number");
            if (currTableNumber.equals(tableNumber)) {
                isTableExist = true;
                break;
            }
        }
        System.out.println("Table with number " + tableNumber + " exist: " + "< " + isTableExist + " >");
        return isTableExist;
    }


}