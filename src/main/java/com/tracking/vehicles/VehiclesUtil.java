/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tracking.vehicles;

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
public class VehiclesUtil {

    public static final String ERROR_CODE = "999";
    public static final String SUCCESS_CODE = "000";
    Logging logger;

    public VehiclesUtil() {
        logger = new Logging();
    }

    public int createVehicle(JsonObject data) {
        int result = 0;
        DBConnection dbConnection = new DBConnection();
        StringBuilder query = new StringBuilder("INSERT INTO Vehicles (");
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

    public JsonArray fetchVehicles() {
        JsonArray vehicles = new JsonArray();
        String query = "SELECT v.*, "
             + "u.FirstName + ' ' + u.LastName AS DriverName, "
             + "vt.Name AS VehicleTypeName "
             + "FROM Vehicles v "
             + "LEFT JOIN Users u ON u.Id = v.Driver "
             + "LEFT JOIN VehicleTypes vt ON vt.Id = v.VehicleType";
        
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
                    vehicles.add(jo);
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

        return vehicles;
    }

    public JsonObject fetchVehiclesById(String Id) {
        JsonObject vehicle = new JsonObject();
        String query = "SELECT v.*, "
             + "u.FirstName + ' ' + u.LastName AS DriverName, "
             + "vt.Name AS VehicleTypeName "
             + "FROM Vehicles v "
             + "LEFT JOIN Users u ON u.Id = v.Driver "
             + "LEFT JOIN VehicleTypes vt ON vt.Id = v.VehicleType WHERE v.Id = ?";
        DBConnection dbConnection = new DBConnection();
        Connection connection = null;

        try {
            connection = dbConnection.getConnection();
            try (PreparedStatement prQuery = connection.prepareStatement(query)) {
                prQuery.setString(1, Id); // Set the ID parameter

                ResultSet rs = prQuery.executeQuery();
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                if (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object columnValue = rs.getObject(i);

                        if (columnValue == null) {
                            vehicle.put(columnName, (Object) null);
                        } else {
                            switch (metaData.getColumnType(i)) {
                                case Types.INTEGER:
                                case Types.BIGINT:
                                case Types.SMALLINT:
                                case Types.TINYINT:
                                    vehicle.put(columnName, String.valueOf(rs.getInt(i)));
                                    break;
                                case Types.DOUBLE:
                                case Types.FLOAT:
                                case Types.DECIMAL:
                                case Types.NUMERIC:
                                    vehicle.put(columnName, String.valueOf(rs.getDouble(i)));
                                    break;
                                case Types.BOOLEAN:
                                    vehicle.put(columnName, String.valueOf(rs.getBoolean(i)));
                                    break;
                                case Types.DATE:
                                case Types.TIMESTAMP:
                                    vehicle.put(columnName, columnValue.toString());
                                    break;
                                default:
                                    vehicle.put(columnName, rs.getString(i));
                                    break;
                            }
                        }
                    }
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

        return vehicle;
    }

    public int updateVehicles(JsonObject data) {
        int result = 0;
        DBConnection dbConnection = new DBConnection();

        if (!data.containsKey("Id")) {
            System.err.println("Missing 'id' field in update data.");
            return 0; // You must have an identifier for the WHERE clause
        }

        StringBuilder query = new StringBuilder("UPDATE Vehicles SET ");
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

    public int createVehicleType(JsonObject data) {
        int result = 0;
        DBConnection dbConnection = new DBConnection();
        StringBuilder query = new StringBuilder("INSERT INTO VehicleTypes (");
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

    public JsonArray fetchVehicleTypes() {
        JsonArray vehicleTypes = new JsonArray();
        String query = "SELECT * FROM VehicleTypes";
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
                    vehicleTypes.add(jo);
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

        return vehicleTypes;
    }

    public JsonObject fetchVehicleTypesById(String Id) {
        JsonObject vehicleType = new JsonObject();
        String query = "SELECT * FROM VehicleTypes WHERE Id = ?";
        DBConnection dbConnection = new DBConnection();
        Connection connection = null;

        try {
            connection = dbConnection.getConnection();
            try (PreparedStatement prQuery = connection.prepareStatement(query)) {
                prQuery.setString(1, Id); // Set the ID parameter

                ResultSet rs = prQuery.executeQuery();
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                if (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object columnValue = rs.getObject(i);

                        if (columnValue == null) {
                            vehicleType.put(columnName, (Object) null);
                        } else {
                            switch (metaData.getColumnType(i)) {
                                case Types.INTEGER:
                                case Types.BIGINT:
                                case Types.SMALLINT:
                                case Types.TINYINT:
                                    vehicleType.put(columnName, String.valueOf(rs.getInt(i)));
                                    break;
                                case Types.DOUBLE:
                                case Types.FLOAT:
                                case Types.DECIMAL:
                                case Types.NUMERIC:
                                    vehicleType.put(columnName, String.valueOf(rs.getDouble(i)));
                                    break;
                                case Types.BOOLEAN:
                                    vehicleType.put(columnName, String.valueOf(rs.getBoolean(i)));
                                    break;
                                case Types.DATE:
                                case Types.TIMESTAMP:
                                    vehicleType.put(columnName, columnValue.toString());
                                    break;
                                default:
                                    vehicleType.put(columnName, rs.getString(i));
                                    break;
                            }
                        }
                    }
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

        return vehicleType;
    }

    public int updateVehicleTypes(JsonObject data) {
        int result = 0;
        DBConnection dbConnection = new DBConnection();

        if (!data.containsKey("Id")) {
            System.err.println("Missing 'id' field in update data.");
            return 0; // You must have an identifier for the WHERE clause
        }

        StringBuilder query = new StringBuilder("UPDATE VehicleTypes SET ");
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
