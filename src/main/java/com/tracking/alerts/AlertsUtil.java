/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tracking.alerts;

import com.tracking.datasource.DBConnection;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import log.Logging;

/**
 *
 * @author Calvin
 */
public class AlertsUtil {
    public static final String ERROR_CODE = "999";
    public static final String SUCCESS_CODE = "000";
    Logging logger;

    public AlertsUtil() {
        logger = new Logging();
    }
    
    public JsonArray fetchAlerts() {
        JsonArray alerts = new JsonArray();
        String query = "SELECT * FROM Alerts";
        DBConnection dbConnection = new DBConnection();
        Connection connection = null;

        try {
            connection = dbConnection.getConnection();
            try (PreparedStatement prQuery = connection.prepareStatement(query)) {

                ResultSet rs = prQuery.executeQuery();
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    JsonObject jo = new JsonObject();
                    // Dynamically iterate over all the columns
                    for (int i = 1; i <= columnCount; i++) {

                        String columnName = metaData.getColumnName(i);
                        Object columnValue = rs.getObject(i);

                        //Handle null values and convert to appropriate Json Type
                        if (columnValue == null) {
                            jo.put(columnName, (Object) null);
                        } else {
                            switch (metaData.getColumnType(i)) {
                                case Types.INTEGER:
                                case Types.BIGINT:
                                case Types.SMALLINT:
                                case Types.TINYINT:
                                    jo.put(columnName, String.valueOf(rs.getInt(i)));
                                    break;
                                case Types.DOUBLE:
                                case Types.FLOAT:
                                case Types.DECIMAL:
                                case Types.NUMERIC:
                                    jo.put(columnName, String.valueOf(rs.getDouble(i)));
                                    break;
                                case Types.BOOLEAN:
                                    jo.put(columnName, String.valueOf(rs.getBoolean(i)));
                                    break;
                                case Types.DATE:
                                case Types.TIMESTAMP:
                                    // Convert date/timestamp to string (or customize format if needed)
                                    jo.put(columnName, columnValue.toString());
                                    break;
                                default:
                                    // Default to string for VARCHAR, NVARCHAR, etc.
                                    jo.put(columnName, rs.getString(i));
                                    break;
                            }
                        }
                    }
                    alerts.add(jo);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                connection.close();
            } catch (Exception sql) {
                sql.printStackTrace();
            }
        }

        return alerts;
    }
    
    public int createAlert(JsonObject data) {
        int result = 0;
        DBConnection dbConnection = new DBConnection();
        StringBuilder query = new StringBuilder("INSERT INTO Alerts (");
        StringBuilder placeholders = new StringBuilder("VALUES (");

        List<Object> values = new ArrayList<>();
        Set<String> excludedFields = new HashSet<>(Arrays.asList("username", "password", "processingCode", "ip_address", "validation", "channel"));

        // Build column names and placeholders
        for (String key : data.fieldNames()) {
            if (excludedFields.contains(key)) {
                continue; // Skip this field
            }
            query.append(key).append(", ");
            placeholders.append("?, ");
            values.add(data.getValue(key));
        }

        // Remove trailing comma and space
        query.setLength(query.length() - 2);
        placeholders.setLength(placeholders.length() - 2);

        query.append(") ");
        placeholders.append(")");
        query.append(placeholders);

        try (Connection connection = dbConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(query.toString(), Statement.RETURN_GENERATED_KEYS)) {

            // Set values dynamically
            for (int i = 0; i < values.size(); i++) {
                ps.setObject(i + 1, values.get(i));
            }

            int affectedRows = ps.executeUpdate();

            // Optionally get the generated key (e.g., ID)
            if (affectedRows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        result = rs.getInt(1); // Assuming the ID is the first column
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace(); // Consider logging instead
        }

        return result;
    }
    
    public int updateAlerts(JsonObject data) {
        int result = 0;
        DBConnection dbConnection = new DBConnection();

        if (!data.containsKey("Id")) {
            System.err.println("Missing 'id' field in update data.");
            return 0; // You must have an identifier for the WHERE clause
        }

        StringBuilder query = new StringBuilder("UPDATE Alerts SET ");
        List<Object> values = new ArrayList<>();
        Set<String> excludedFields = new HashSet<>(Arrays.asList("username", "password", "processingCode", "ip_address", "validation", "Id", "channel"));

        // Build column names and placeholders
        for (String key : data.fieldNames()) {
            if (excludedFields.contains(key)) {
                continue; // Skip this field
            }
            if (!key.equals("Id")) { // Exclude ID from SET clause
                query.append(key).append(" = ?, ");
                values.add(data.getValue(key));
            }
        }

        // Remove trailing comma and space
        query.setLength(query.length() - 2);

        query.append(" WHERE Id = ?");
        values.add(data.getString("Id")); // Add ID for WHERE clause

        try (Connection connection = dbConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(query.toString())) {

            // Set all values in order
            for (int i = 0; i < values.size(); i++) {
                ps.setObject(i + 1, values.get(i));
            }

            result = ps.executeUpdate(); // Returns number of affected rows

        } catch (Exception e) {
            e.printStackTrace(); // Use logger in production
        }

        return result;
    }
}
