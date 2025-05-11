package com.tracking.user;

import com.tracking.datasource.DBConnection;
import com.tracking.util.Common;
import com.tracking.util.LoginTokens;
import com.tracking.util.ProjectConstants;
import com.tracking.util.Utilities;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import log.Logging;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.IntStream;

public class UserUtil {

    public static final String ERROR_CODE = "999";
    public static final String SUCCESS_CODE = "000";
    Logging logger;

    public UserUtil() {
        logger = new Logging();
    }

    // Method to generate a random alphanumeric password of a specific length
    public String generateRandomPassword(int len) {
        // ASCII range – alphanumeric (0-9, a-z, A-Z)
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        // each iteration of the loop randomly chooses a character from the given
        // ASCII range and appends it to the `StringBuilder` instance
        for (int i = 0; i < len; i++) {
            int randomIndex = random.nextInt(chars.length());
            sb.append(chars.charAt(randomIndex));
        }

        return sb.toString();
    }

    public JsonObject registerUser(JsonObject request) {
        Common common = new Common();
        String firstName, lastName, email, phoneNumber, role, userName, channel, idNumber;
        try {
            firstName = request.getString("FirstName");
            lastName = request.getString("LastName");
            email = request.getString("Email");
            idNumber = request.getString("IdNumber");
            phoneNumber = request.getString("PhoneNumber");
            role = request.getString("Role");
            userName = request.getString("User");
            channel = request.getString("Channel");
        } catch (Exception e) {
            //e.printStackTrace();
            request.clear();
            request
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Error parsing data");
            return request;
        }

        request.clear();

        phoneNumber = new Utilities().formatPhone(phoneNumber);
        String password = "";

        if (channel.trim().equalsIgnoreCase("app")) {
            password = common.generateNewPinRandomFour();
        } else {
            password = generateRandomPassword(8);
        }

//        String password = generateRandomPassword(8);
        String hashPassword = common.generatedHashedPin(password, "A.B.", "12345678");

        JsonObject roleDetails = fetchRoleDetails("id", role);
        if (!roleDetails.getBoolean("successIndicator")) {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Invalid Role selected");
            return request;
        }

        String sql = "INSERT INTO Users(FirstName,LastName,IdNumber,UserName,Email,PhoneNumber,"
                + "Role,Channel,Uuid,Password,Status,CreatedAt,CreatedBy,UpdatedAt,UpdatedBy) VALUES(?,?,?,?,?,?,?,?,NEWID(),?,1,GETDATE(),?,GETDATE(),?)";

        String insertLoginValidation = "INSERT INTO LoginValidation(UserId,Password,LoginTrials,ChangePassword,Status,OTPVerified,OTP,OTPExpiry) \n"
                + "   VALUES(?,?,?,?,?,?,?,DATEADD(Minute,?,GETDATE()))";


        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
                PreparedStatement prLoginVal = connection.prepareStatement(insertLoginValidation);) {

            connection.setAutoCommit(false);

            preparedStatement.setString(1, firstName);
            preparedStatement.setString(2, lastName);
            preparedStatement.setString(3, idNumber);
            preparedStatement.setString(4, userName);
            preparedStatement.setString(5, email);
            preparedStatement.setString(6, phoneNumber);
            preparedStatement.setString(7, role);
            preparedStatement.setString(8, channel);
            preparedStatement.setString(9, hashPassword);
            preparedStatement.setString(10, request.getString("CreatedBy"));
            preparedStatement.setString(11, request.getString("UpdatedBy"));
            if (preparedStatement.executeUpdate() == 0) {
                throw new SQLException("Failed to add user to USERS TABLE");
            }

            String userUUID = "";

            String fetchUserUUID = "SELECT Uuid FROM Users WHERE email = ? ";
            PreparedStatement prFetchUUID = connection.prepareStatement(fetchUserUUID);
            prFetchUUID.setString(1, email);
            boolean y = prFetchUUID.execute();
            ResultSet resultSet = prFetchUUID.getResultSet();
            if (resultSet.next()) {
                userUUID = resultSet.getString("Uuid");
            } else {
                throw new SQLException("Failed to fetch user uuid from USERS TABLE");
            }
            resultSet.close();

            String otp = common.generateRandom(6);
            String otpHash = common.generatedHashedPin(otp, "1", "1");

            prLoginVal.setString(1, userUUID);
            prLoginVal.setString(2, hashPassword);
            prLoginVal.setInt(3, 0); // Trials
            prLoginVal.setInt(4, 1); // Change Password
            prLoginVal.setInt(5, 1); // Status
            prLoginVal.setInt(6, 0); // OTP Verified
            prLoginVal.setString(7, otpHash);
            prLoginVal.setInt(8, 10); // 10 mns
            prLoginVal.executeUpdate();

            // USer Branches
//            for (int x = 0; x < branches.size(); x++) {
//                prInsertUsersBranches.setString(1, userUUID);
//                prInsertUsersBranches.setString(2, branches.getString(x));
//                prInsertUsersBranches.addBatch();
//            }
//            int[] insertedBranches = prInsertUsersBranches.executeBatch();
            //System.out.println("\n USer Branches "+ insertedBranches);

            String emailBody = "Dear " + firstName.toUpperCase() + " " + lastName.toUpperCase()
                    + ". You have been registered to use " + ProjectConstants.COMPANY_NAME + " as a " + roleDetails.getString("Name")
                    + "Your password is " + password
                    + ". Visit " + ProjectConstants.PORTAL_URL + "\" here to login to the system"
                    + "Thank you.";

            request
                    .put("emailRecipient", email)
                    .put("emailSubject", "REGISTRATION")
                    .put("emailBody", emailBody);

            request
                    .put("phonenumber", phoneNumber)
                    .put("msg", emailBody);

            request
                    .put("responseCode", SUCCESS_CODE)
                    .put("responseDescription", "Success! User created");

            logger.applicationLog(logger.logPreString() + "PASSWORD - " + email + " - " + password, "", 99);

            connection.commit();

        } catch (SQLException throwables) {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Failed to register User");

            try {
                connection.rollback();
            } catch (SQLException e) {
                //e.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + e.getLocalizedMessage() + "\n\n", "", 6);
            }

            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }
        return request;
    }

    public JsonObject updateUser(JsonObject request) {
        String firstName, lastName, email, phoneNumber, userType, userUUID, user;
        JsonArray branches;
        double loanLimit;
        try {
            userUUID = request.getString("uuid_user");
            firstName = request.getString("firstName");
            lastName = request.getString("lastName");
            email = request.getString("email");
            branches = request.getJsonArray("branches");

            // Convert JsonArray to List<String> while filtering out duplicates
//            Set<String> uniqueIdentifiers = new HashSet<>();
//            JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
//            IntStream.range(0, branches.size())
//                    .mapToObj(branches::getString)
//                    .filter(uniqueIdentifiers::add)
//                    .forEach(jsonArrayBuilder::add);
            phoneNumber = request.getString("phoneNumber");
            userType = request.getString("userType");
            user = request.getString("user"); // Login user
        } catch (Exception e) {
            //e.printStackTrace();
            request.clear();
            request
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Invalid Request Data");
            return request;
        }

        request.clear();

        phoneNumber = new Utilities().formatPhone(phoneNumber);

        JsonObject roleDetails = fetchRoleDetails("id", userType);
        if (!roleDetails.getBoolean("successIndicator")) {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Invalid Role selected");
            return request;
        }

        JsonObject userDetails = fetchUserDetails("u.uuid", userUUID);
        if (!userDetails.getBoolean("successIndicator")) {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Unable to fetch user details");
            return request;
        }

        String sql = "UPDATE users SET first_name = ?, last_name = ?, phone_number = ? , email = ? , [type] = ?,updated_by=?,updated_at = GETDATE() WHERE uuid = ?";

        String deleteUsersBranches = "DELETE FROM usersBranches WHERE UserId = ?";
        String insertUsersBranches = "INSERT INTO usersBranches(UserId,BranchId) VALUES(?,?)";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
                PreparedStatement prDeleteUsersBranches = connection.prepareStatement(deleteUsersBranches);
                PreparedStatement prUpdateUsersBranches = connection.prepareStatement(insertUsersBranches);) {

            connection.setAutoCommit(false);

            preparedStatement.setString(1, firstName);
            preparedStatement.setString(2, lastName);
            preparedStatement.setString(3, phoneNumber);
            preparedStatement.setString(4, email);
            preparedStatement.setString(5, userType);
            preparedStatement.setString(6, user);
            preparedStatement.setString(7, userUUID);
            int insertedRows = preparedStatement.executeUpdate();
            if (insertedRows == 0) {
                throw new SQLException("Failed to update User " + firstName);
            }

            prDeleteUsersBranches.setString(1, userUUID);
            prDeleteUsersBranches.executeUpdate();

            // USer Branches
            for (int x = 0; x < branches.size(); x++) {
                prUpdateUsersBranches.setString(1, userUUID);
                prUpdateUsersBranches.setString(2, branches.getString(x));
                prUpdateUsersBranches.addBatch();
            }
            int[] insertedBranches = prUpdateUsersBranches.executeBatch();
//            //System.out.println("\n User Branches "+ Arrays.toString(insertedBranches));

            connection.commit();

            request
                    .put("responseCode", SUCCESS_CODE)
                    .put("responseDescription", "Success! " + insertedRows + " User updated");

        } catch (SQLException throwables) {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Failed to register User");

            try {
                connection.rollback();
            } catch (SQLException e) {
                //e.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + e.getLocalizedMessage() + "\n\n", "", 6);

            }

            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }

        return request;
    }

    public JsonObject activateDeactivateUser(JsonObject request) {
        String userId, user;
        byte status;
        try {
            userId = request.getString("user_uuid");
            user = request.getString("user");
            status = Byte.parseByte(request.getString("status"));

        } catch (Exception e) {
            //e.printStackTrace();
            request.clear();
            request
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Invalid Request Data");
            return request;
        }

        JsonObject userDetails = fetchUserDetails("uuid", userId);
        if (!userDetails.getBoolean("successIndicator")) {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Unable to fetch user details");
            return request;
        }

        String sql = " UPDATE users SET [status] = ?, updated_by = ?, updated_at = GETDATE() WHERE uuid = ?";
        String lv_sql = " UPDATE login_validation SET [status] = ?,  updated_at = GETDATE() WHERE uuid = ?";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
                PreparedStatement prLoginValidation = connection.prepareStatement(lv_sql)) {

            preparedStatement.setByte(1, status);
            preparedStatement.setString(2, user);
            preparedStatement.setString(3, userId);
            int insertedRows = preparedStatement.executeUpdate();
            if (insertedRows > 0) {
                prLoginValidation.setByte(1, status);
                prLoginValidation.setString(2, userId);
                prLoginValidation.executeUpdate();

                request
                        .put("responseCode", SUCCESS_CODE)
                        .put("responseDescription", "Success! " + insertedRows + " User updated");
            } else {
                request
                        .put("responseCode", ERROR_CODE)
                        .put("responseDescription", "Error! Unable to update user");
            }

        } catch (SQLException throwables) {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Failed to activate User");

            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }

        return request;
    }

    public JsonObject fetchLoginValidationDetails(String column, String word) {
        JsonObject loginObject = new JsonObject();

        DBConnection conn = new DBConnection();
        try {
            String call = "SELECT Id,UserId,Password,LoginTrials,ChangePassword,Status,OtpVerified,"
                    + "OTP,OTPExpiry,CreatedAt,UpdatedAt FROM LoginValidation WHERE " + column + "='" + word + "'";

            ResultSet rs = conn.query_all(call);
            if (rs.next()) {
                loginObject
                        .put("successIndicator", true)
                        .put("Id", rs.getString("Id"))
                        .put("UserId", rs.getString("UserId"))
                        .put("Password", rs.getString("Password"))
                        .put("LoginTrials", String.valueOf(rs.getInt("LoginTrials")))
                        .put("ChangePassword", rs.getString("ChangePassword"))
                        .put("Status", rs.getString("Status"))
                        .put("OTPVerified", rs.getString("OTPVerified"))
                        .put("OTP", rs.getString("OTP"))
                        .put("OTPExpiry", rs.getString("OTPExpiry"))
                        .put("CreatedAt", rs.getString("createdat"))
                        .put("OTPExpiry", rs.getString("UpdatedAt"));
            } else {
                loginObject.put("successIndicator", false);
            }
        } catch (SQLException ex) {
            loginObject.put("successIndicator", false);
            ex.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error " + ex.getMessage() + "\n", "", 6);
        } finally {
            conn.closeConn();
        }
        return loginObject;
    }

    public JsonArray fetchUsersWithUsersBranchesTable(String column, String value) {
        String sql = " SELECT u.id,ub.BranchId,b.[Name],first_name,last_name,phone_number,email,\n"
                + "  uuid,[type],u.creator_id,[status],u.created_at, r.[name] AS RoleName FROM users u \n"
                + " INNER JOIN usersBranches ub ON ub.UserId = u.uuid LEFT JOIN Branches b ON b.Id = ub.BranchId\n"
                + " LEFT JOIN roles r ON r.id = u.[type]"
                + " WHERE " + column + " = '" + value + "' ORDER BY first_name ASC";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        JsonArray array = new JsonArray();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.execute();

            ResultSet resultSet = preparedStatement.getResultSet();
            while (resultSet.next()) {
                JsonObject obj = new JsonObject();
                obj
                        //                        .put("id",resultSet.getString("id"))
                        .put("branchId", resultSet.getString("BranchId"))
                        .put("branchName", resultSet.getString("Name"))
                        .put("firstName", resultSet.getString("first_name"))
                        .put("lastName", resultSet.getString("last_name"))
                        .put("phoneNumber", resultSet.getString("phone_number"))
                        .put("email", resultSet.getString("email"))
                        .put("uuid", resultSet.getString("uuid"))
                        .put("type", resultSet.getString("type"))
                        .put("roleName", resultSet.getString("RoleName"))
                        .put("maker", resultSet.getString("creator_id"))
                        .put("status", resultSet.getString("status"))
                        .put("createdAt", resultSet.getString("created_at"));

                array.add(obj);
            }

        } catch (SQLException throwables) {
            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }

        return array;
    }

    public JsonObject fetchUsers(String column, String value) {
        JsonObject res = new JsonObject();
        String sql = "SELECT u.id,first_name,last_name,phone_number,email,\n"
                + "  uuid,[type],u.creator_id,[status],u.created_at,u.channel,r.[name] AS RoleName FROM users u\n"
                + "  LEFT JOIN roles r ON r.id = u.[type]"
                + " WHERE " + column + " = '" + value + "'  ORDER BY created_at DESC";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        JsonArray array = new JsonArray();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.execute();

            ResultSet resultSet = preparedStatement.getResultSet();
            while (resultSet.next()) {
                JsonObject obj = new JsonObject();
                obj
                        .put("id", String.valueOf(resultSet.getInt("id")))
                        .put("firstName", resultSet.getString("first_name"))
                        .put("lastName", resultSet.getString("last_name"))
                        .put("phoneNumber", resultSet.getString("phone_number"))
                        .put("email", resultSet.getString("email"))
                        .put("uuid", resultSet.getString("uuid"))
                        .put("type", resultSet.getString("type"))
                        .put("roleName", resultSet.getString("RoleName"))
                        .put("maker", resultSet.getString("creator_id"))
                        .put("status", resultSet.getString("status"))
                        .put("createdAt", resultSet.getString("created_at"))
                        .put("channel", resultSet.getString("channel"));

                array.add(obj);
            }

        } catch (SQLException throwables) {
            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }

        res.put("data", array);
        return res;
    }

    public JsonArray fetchUserDetails(String searchWord) {
        JsonArray results = new JsonArray();
        String sql = "SELECT u.id, first_name, last_name, phone_number, email, "
                + "uuid, [type], u.creator_id, [status], u.created_at, u.channel, r.[name] AS RoleName "
                + "FROM users u "
                + "LEFT JOIN roles r ON r.id = u.[type] "
                + "WHERE first_name LIKE ? OR last_name LIKE ? OR email LIKE ? OR phone_number LIKE ?";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        try (
                PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            // Use LIKE with wildcards for partial matches
            String searchPattern = "%" + searchWord + "%";
            preparedStatement.setString(1, searchPattern); // first_name
            preparedStatement.setString(2, searchPattern); // last_name
            preparedStatement.setString(3, searchPattern); // email
            preparedStatement.setString(4, searchPattern); // phone_number

            ResultSet resultSet = preparedStatement.executeQuery(); // Use executeQuery directly

            while (resultSet.next()) {
                JsonObject res = new JsonObject()
                        .put("id", resultSet.getString("id"))
                        .put("firstName", resultSet.getString("first_name"))
                        .put("lastName", resultSet.getString("last_name"))
                        .put("phoneNumber", resultSet.getString("phone_number"))
                        .put("email", resultSet.getString("email"))
                        .put("uuid", resultSet.getString("uuid"))
                        .put("type", resultSet.getString("type"))
                        .put("roleName", resultSet.getString("RoleName"))
                        .put("maker", resultSet.getString("creator_id"))
                        .put("status", resultSet.getString("status"))
                        .put("createdAt", resultSet.getString("created_at"))
                        .put("channel", resultSet.getString("channel"));

                // Fetch additional details for this user
                String userUUID = resultSet.getString("uuid");
                JsonArray usersBranchesJsonArray = fetchUsersBranchesAsBranchesJsonArray("UserId", userUUID);
                res.put("userBranches", usersBranchesJsonArray);

                JsonArray usersBranchesDetails = fetchUsersBranchesDetails("UserId", userUUID);
                res.put("userBranchesDetails", usersBranchesDetails);

                results.add(res); // Add this user to the results array
            }
        } catch (SQLException throwables) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.applicationLog(logger.logPreString() + "Error - " + e.getLocalizedMessage() + "\n\n", "", 6);
            }
            throwables.getMessage();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.getMessage();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }
        return results;
    }

    public JsonObject fetchUserDetails(String searchColumn, String searchWord) {
        JsonObject res = new JsonObject();
        String sql = "SELECT u.id,FirstName,LastName,PhoneNumber,Email,\n"
                + "  Uuid,Role,u.CreatedBy,Status,u.CreatedAt,u.Channel,r.Name AS RoleName FROM Users u\n"
                + "  LEFT JOIN roles r ON r.Id = u.Role  WHERE " + searchColumn + " = ?";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        try (
                PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, searchWord);
            preparedStatement.execute();

            ResultSet resultSet = preparedStatement.getResultSet();
            if (resultSet.next()) {
                res
                        .put("successIndicator", true)
                        .put("Id", resultSet.getString("Id"))
                        .put("FirstName", resultSet.getString("FirstName"))
                        .put("LastName", resultSet.getString("LastName"))
                        .put("PhoneNumber", resultSet.getString("PhoneNumber"))
                        .put("Email", resultSet.getString("Email"))
                        .put("Uuid", resultSet.getString("Uuid"))
                        .put("Role", resultSet.getString("Role"))
                        .put("RoleName", resultSet.getString("RoleName"))
                        .put("Maker", resultSet.getString("CreatedBy"))
                        .put("Status", resultSet.getString("Status"))
                        .put("CreatedAt", resultSet.getString("CreatedAt"))
                        .put("Channel", resultSet.getString("Channel"));

//                String userUUID = resultSet.getString("Uuid");
//                JsonArray usersBranchesJsonArray = fetchUsersBranchesAsBranchesJsonArray("UserId", userUUID);
//                res.put("userBranches", usersBranchesJsonArray);
//
//                JsonArray usersBranchesDetails = fetchUsersBranchesDetails("UserId", userUUID);
//                res.put("userBranchesDetails", usersBranchesDetails);
            } else {
                res.put("successIndicator", false);
            }

        } catch (SQLException throwables) {
            res.put("successIndicator", false);
            try {
                connection.close();
            } catch (SQLException e) {
                //e.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + e.getLocalizedMessage() + "\n\n", "", 6);
            }

            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }
        return res;
    }

    public JsonArray fetchUsersBranchesDetails(String searchColumn, String searchWord) {
        JsonArray array = new JsonArray();
        String sql = "SELECT TOP (1000) ub.[Id],[UserId],[BranchId],ub.[CreatedDate],b.[Name] as BranchName,"
                + "b.[Code] as BranchCode, u.first_name + ' ' + u.last_name AS UserFullName\n"
                + "  FROM [dbo].[usersBranches] ub LEFT JOIN Branches b ON b.Id = ub.BranchId "
                + "LEFT JOIN users u ON u.uuid = ub.UserId  WHERE " + searchColumn + " = ? ORDER BY b.[Name] ASC";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, searchWord);
            preparedStatement.execute();

            ResultSet resultSet = preparedStatement.getResultSet();
            while (resultSet.next()) {
                JsonObject obj = new JsonObject();
                obj
                        .put("id", resultSet.getString("Id"))
                        .put("userId", resultSet.getString("UserId"))
                        .put("branchId", resultSet.getString("BranchId"))
                        .put("userFullName", resultSet.getString("UserFullName"))
                        .put("branchName", resultSet.getString("BranchName"))
                        .put("branchCode", resultSet.getString("BranchCode"))
                        .put("createdDate", resultSet.getString("CreatedDate"));
                array.add(obj);
            }

        } catch (SQLException throwables) {
            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }
        return array;
    }

    public JsonArray fetchUsersBranchesAsBranchesJsonArray(String searchColumn, String searchWord) {
        JsonArray array = new JsonArray();
        String sql = "SELECT TOP (100) [Id],[UserId],[BranchId],[CreatedDate]"
                + "  FROM [dbo].[usersBranches]  WHERE " + searchColumn + " = ?";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, searchWord);
            preparedStatement.execute();

            ResultSet resultSet = preparedStatement.getResultSet();
            while (resultSet.next()) {
                array.add(resultSet.getString("BranchId"));
            }

        } catch (SQLException throwables) {
            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }
        return array;
    }

    public JsonObject login(JsonObject request) {
        DBConnection dbConnection = new DBConnection();
        String email, password;
        try {
            email = request.getString("email");
            password = request.getString("user_password");
        } catch (Exception e) {
            //e.printStackTrace();
            request.clear();
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Error parsing data");
            return request;
        }
        request.clear();

//        System.out.println("DEFAULT PASSWORD" + new Common().generatedHashedPin("pass.123", "A.B.", "12345678"));
        JsonObject userDetails = fetchUserDetails("email", email);
        if (!userDetails.getBoolean("successIndicator")) {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Invalid Email or Password");
//            //System.out.println("FAILED TO FETCH USER DATA " + userDetails);
            return request;
        }
        String uuid = userDetails.getString("Uuid");
        String phoneNumber = userDetails.getString("PhoneNumber");
        String name = userDetails.getString("FirstName") + " " + userDetails.getString("LastName");

        if (userDetails.getString("Status").equals("0")) {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! User has been deactivated. Contact System Administrator.");
            return request;
        }

        JsonObject loginValidationDetails = fetchLoginValidationDetails("UserId", uuid);
        if (!loginValidationDetails.getBoolean("successIndicator")) {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Invalid Email or Password");
//            //System.out.println("FAILED TO FETCH LOGIN DATA " + loginValidationDetails);
            return request;
        }
        String lv_id = loginValidationDetails.getString("Id");
        String lv_password = loginValidationDetails.getString("Password");
        int loginTrials = Integer.parseInt(loginValidationDetails.getString("LoginTrials"));

        String hashPassword = new Common().generatedHashedPin(password, "A.B.", "12345678");

        // Check the number of failed attempts
        if (loginTrials < 3) {
            if (hashPassword.equals(lv_password)) {

                String jsonObjectToken = LoginTokens.generateCustomJsonObjectToken(uuid);
                request.put("json_token", jsonObjectToken);

                String encoded = Base64.getEncoder().encodeToString(uuid.getBytes(StandardCharsets.UTF_8));
                request.put("token", encoded);
                request
                        .put("responseCode", SUCCESS_CODE)
                        .put("responseDescription", "Success! User Authenticated");

                // OTP
                String otp = new Common().generateRandom(6);
                String hashedOTP = new Common().generatedHashedPin(otp, "1", "2");

                String sql = "UPDATE LoginValidation SET LoginTrials = '0', OTPVerified='0', OTP = '" + hashedOTP + "',"
                        + "OTPExpiry=DATEADD(Minute,2,GETDATE()),UpdatedAt=GETDATE() WHERE Id= '" + lv_id + "'";

                int i = dbConnection.update_db(sql);
                System.out.println("UPDATED LOGIN VAL:::::: " + i);

                String otpSMS = "Dear " + name.toUpperCase() + ", your One Time Password is " + otp + ".";

                request
                        .put("emailRecipient", email)
                        .put("emailSubject", "OTP")
                        .put("emailBody", otpSMS);

                request
                        .put("phonenumber", phoneNumber)
                        .put("msg", otpSMS);

                //System.out.println("\n OTP " + otp + " - " + i);
            } else {
                loginTrials = loginTrials + 1;

                String sql = "UPDATE LoginValidation SET LoginTrials='" + loginTrials + "'"
                        + ",UpdatedAt=GETDATE() WHERE Id= '" + lv_id + "'";

                int k = dbConnection.update_db(sql);

                request
                        .put("responseCode", ERROR_CODE)
                        .put("responseDescription", "Error! Invalid Email or Password. " + (3 - loginTrials) + " remaining before account is locked");
            }

        } else {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Number of login trials exceeded. Contact System Admin!");
        }

        dbConnection.closeConn();
        return request;
    }

    public JsonObject otpVerification(JsonObject request) {
        String otp = request.getString("otp");
        String user_uuid = request.getString("user_uuid");
        request.clear();

        String hashedOTP = new Common().generatedHashedPin(otp, "1", "2");

        JsonObject loginValidationDetails = fetchLoginValidationDetails("UserId", user_uuid);
        if (!loginValidationDetails.getBoolean("successIndicator")) {
            return request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Failed to fetch login details");
        }
        String lv_id = loginValidationDetails.getString("Id");
        String lv_otpHash = loginValidationDetails.getString("OTP");
        String lv_changePassword = loginValidationDetails.getString("ChangePassword");

        System.out.println("HASHEDOTP::::: " + hashedOTP);
        System.out.println("LOGINVALDETAILS::::: " + loginValidationDetails);
        
        if (!hashedOTP.equals(lv_otpHash)) {
            return request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! OTP validation not successful");
        }

        String sql = "UPDATE LoginValidation SET OTPVerified = '1',Reference = '" + otp + "',UpdatedAt = GETDATE() WHERE Id = " + lv_id + "";
        DBConnection dbConnection = new DBConnection();
        int i = dbConnection.update_db(sql);
        if (i == 1) {
            request
                    .put("responseCode", SUCCESS_CODE)
                    .put("responseDescription", "Success! OTP Validated");

            JsonObject userDetails = fetchUserDetails("uuid", user_uuid);
            if (!userDetails.getBoolean("successIndicator")) {
                request
                        .put("responseCode", ERROR_CODE)
                        .put("responseDescription", "Error! Unable to fetch user details");
                return request;
            }
            String name = userDetails.getString("FirstName") + " " + userDetails.getString("LastName");
            String role = userDetails.getString("Role");
            String roleName = userDetails.getString("RoleName");
            String phoneNumber = userDetails.getString("PhoneNumber");
            String email = userDetails.getString("Email");

//            JsonArray userBranches = userDetails.getJsonArray("userBranchesDetails");

            request
                    .put("roleName", roleName)
                    .put("changePassword", lv_changePassword)
                    .put("name", name)
                    .put("phoneNumber", phoneNumber)
                    .put("email", email)
                    .put("rolePermission", fetchRolePermissionsArray("r.[Id]", role));
//                    .put("userBranches", userBranches);
        } else {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "ERROR! OTP not updated");
        }
        dbConnection.closeConn();
        return request;
    }

    public JsonObject logout(JsonObject request) {
        request.clear();
        request
                .put("responseCode", SUCCESS_CODE)
                .put("responseDescription", "Success! Logged Out");

        return request;
    }

    public JsonObject forgotPassword(JsonObject request) {
        Common common = new Common();
        String email, channel;
        try {
            email = request.getString("email");
            channel = request.getString("channel");
        } catch (Exception e) {
            //e.printStackTrace();
            request.clear();
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Error parsing data");
            return request;
        }
        request.clear();

        String password = "";

        if (channel.trim().equalsIgnoreCase("APP")) {
            password = common.generateNewPinRandomFour();
        } else {
            password = generateRandomPassword(8);
        }
        String hashPassword = new Common().generatedHashedPin(password, "A.B.", "12345678");

        JsonObject userDetails = fetchUserDetails("u.email", email);
        if (!userDetails.getBoolean("successIndicator")) {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Invalid Email");
            //System.out.println("FAILED TO FETCH USER DATA");
            return request;
        }

        String uuid = userDetails.getString("uuid");
        String name = userDetails.getString("firstName") + " " + userDetails.getString("lastName");
        String phoneNumber = userDetails.getString("phoneNumber");

        String sql = "UPDATE login_validation SET  login_trials='0', change_password='1', updated_at = GETDATE() , password = ? WHERE uuid = ? ";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, hashPassword);
            preparedStatement.setString(2, uuid);
            preparedStatement.executeUpdate();

            request
                    .put("responseCode", SUCCESS_CODE)
                    .put("responseDescription", "Success! Password changed");

            String emailBody = "Dear " + name.toUpperCase() + ", <br> your " + ProjectConstants.COMPANY_NAME + " password has been reset to " + password
                    + "<br> If you did not initiate a Forgot My Password action, then contact us immediately."
                    + "Click <a href=\"" + ProjectConstants.PORTAL_URL + "\" >here</a> to login <br> Thanks";

            String otpSMS = "Dear " + name.toUpperCase() + ", your " + ProjectConstants.COMPANY_NAME + " password has been reset to " + password
                    + ". If you did not initiate a Forgot My Password action, then contact us immediately.";

            request
                    .put("phonenumber", phoneNumber)
                    .put("msg", otpSMS);

            request
                    .put("emailRecipient", email)
                    .put("emailSubject", "FORGOT PASSWORD")
                    .put("emailBody", emailBody);

        } catch (SQLException throwables) {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! ");

            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }

        // SEND EMAIL
        return request;
    }

    public JsonArray fetchPasswordHistory(String userId) {
        String sql = "SELECT * FROM PasswordHistory WHERE UserId = ?";
        JsonArray results = new JsonArray();

        DBConnection conn = new DBConnection();
        Connection connection = conn.getConnection();

        try (PreparedStatement prUpdatePassHist = connection.prepareStatement(sql)) {
            prUpdatePassHist.setInt(1, Integer.parseInt(userId));

            ResultSet rs = prUpdatePassHist.executeQuery();

            while (rs.next()) {
                JsonObject jo = new JsonObject();

                jo
                        .put("UserId", String.valueOf(rs.getInt("UserId")))
                        .put("PreviousPassword", rs.getString("PreviousPassword"))
                        .put("CreatedAt", rs.getString("CreatedAt"));

                results.add(jo);
            }
        } catch (Exception e) {
            //e.printStackTrace(); 
            logger.applicationLog(logger.logPreString() + "Error - " + e.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
            conn.closeConn();
        }

        return results;
    }

    public JsonObject changePassword(JsonObject request) {
        String user, currentPassword, newPassword;
        try {
            user = request.getString("user");
            currentPassword = request.getString("currentPassword");
            newPassword = request.getString("newPassword");
        } catch (Exception e) {
            //e.printStackTrace();
            request.clear();
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Error parsing data");
            return request;
        }

        request.clear();

        JsonObject userDetails = fetchUserDetails("u.id", user);
        if (!userDetails.getBoolean("successIndicator")) {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Invalid User details");
            //System.out.println("FAILED TO FETCH USER DATA");
            return request;
        }
        String uuid = userDetails.getString("Uuid");
        String email = userDetails.getString("Email");
        String name = userDetails.getString("FirstName") + " " + userDetails.getString("LastName");

        JsonObject loginValidationDetails = fetchLoginValidationDetails("UserId", uuid);
        if (!loginValidationDetails.getBoolean("successIndicator")) {
            request
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Invalid Login details");
            //System.out.println("FAILED TO FETCH LOGIN VALIDATION DATA");
            return request;
        }
        String lv_id = loginValidationDetails.getString("Id");

        String currentPasswordHash = new Common().generatedHashedPin(currentPassword, "A.B.", "12345678");
        String newPasswordHash = new Common().generatedHashedPin(newPassword, "A.B.", "12345678");

        JsonArray pastPasswords = fetchPasswordHistory(user);
        boolean okayToProceed = true;

        for (Object obj : pastPasswords) {
            JsonObject pass = (JsonObject) obj;

            String prevPassHash = pass.getString("PreviousPassword");

            if (prevPassHash.equals(newPasswordHash)) {
                okayToProceed = false;
                request
                        .put("responseCode", ERROR_CODE)
                        .put("responseDescription", "Error! New password must not have been used before. Try again.");
                break;
            }
        }

        if (okayToProceed) {
            if (currentPasswordHash.equals(loginValidationDetails.getString("Password"))) {
                String sql = "UPDATE LoginValidation SET UpdatedAt = GETDATE() , ChangePassword = '0',Password = ? WHERE Id = ? ";
                String sqlPassHist = "INSERT INTO PasswordHistory (UserId,PreviousPassword,CreatedAt,UpdatedAt) "
                        + "VALUES (?,?,GETDATE(),GETDATE())";

                DBConnection dbConnection = new DBConnection();
                Connection connection = dbConnection.getConnection();

                try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
                        PreparedStatement prPassHist = connection.prepareStatement(sqlPassHist)) {
                    preparedStatement.setString(1, newPasswordHash);
                    preparedStatement.setString(2, lv_id);
                    preparedStatement.executeUpdate();

                    prPassHist.setInt(1, Integer.parseInt(user));
                    prPassHist.setString(2, newPasswordHash);
                    prPassHist.executeUpdate();

                    connection.commit();

                    request
                            .put("responseCode", SUCCESS_CODE)
                            .put("responseDescription", "Success! Password changed");

                    String emailBody = "Dear " + name.toUpperCase() + ", your " + ProjectConstants.COMPANY_NAME + " password has been reset ."
                            + " If you did not change your password, Contact us immediately. Thanks";
                    request
                            .put("emailRecipient", email)
                            .put("emailSubject", "PASSWORD RESET")
                            .put("emailBody", emailBody);

                } catch (SQLException throwables) {
                    request
                            .put("responseCode", ERROR_CODE)
                            .put("responseDescription", "Error! ");

                    throwables.printStackTrace();
                    logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
                } finally {
                    try {
                        connection.close();
                    } catch (SQLException throwables) {
                        throwables.printStackTrace();
                        logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
                    }
                    dbConnection.closeConn();
                }

            } else {
                request
                        .put("responseCode", ERROR_CODE)
                        .put("responseDescription", "Error! Invalid Password");

            }
        }

        return request;
    }

    public JsonObject fetchUserPermissions(String searchColumn, String searchWord) {
        JsonObject res = new JsonObject();
        String sql = "SELECT TOP (1000) u.[Id],r.Id AS role_id,p.[Name] AS p_name,FirstName,LastName,PhoneNumber,Email,Status\n"
                + "  FROM [dbo].[Users] u\n"
                + "  LEFT JOIN roles r ON r.[Name] = u.[Role]\n"
                + "  JOIN [RoleHasPermissions] rp ON rp.RoleId = r.Id\n"
                + "  LEFT JOIN [Permissions] p ON p.[Id] = rp.PermissionId\n"
                + "  WHERE " + searchColumn + " = ? ";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        JsonArray array = new JsonArray();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, searchWord);
            preparedStatement.execute();

            ResultSet resultSet = preparedStatement.getResultSet();
            if (resultSet.next()) {
                JsonObject obj = new JsonObject();
                res
                        .put("successIndicator", true)
                        .put("FirstName", resultSet.getString("FirstName"))
                        .put("LastName", resultSet.getString("LastName"))
                        .put("PhoneNumber", resultSet.getString("PhoneNumber"))
                        .put("Email", resultSet.getString("Email"))
                        .put("Type", resultSet.getString("Role"))
                        .put("PermissionName", resultSet.getString("p_name"))
                        .put("Status", resultSet.getString("Status"));
                array.add(obj);
            } else {
                res.put("successIndicator", false);
            }

        } catch (SQLException throwables) {
            res.put("successIndicator", false);

            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }
        res.put("data", array);
        return res;
    }

    public boolean checkUserHasPermission(String searchColumn, String searchWord, String permission) {
        boolean hasPermission = false;
        String sql = "SELECT TOP (100) u.[Id],r.Id AS role_id,p.[Name] AS p_name,FirstName,LastName,PhoneNumber,Email,Status\n"
                + "  FROM [dbo].[Users] u\n"
                + "  LEFT JOIN Roles r ON r.[Id] = u.[Role]\n"
                + "  JOIN [RoleHasPermissions] rp ON rp.RoleId = r.Id\n"
                + "  LEFT JOIN [Permissions] p ON p.[Id] = rp.PermissionId\n"
                + "  WHERE " + searchColumn + " = ? AND p.[Name] = ? ";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, searchWord);
            preparedStatement.setString(2, permission);
            preparedStatement.execute();

            ResultSet resultSet = preparedStatement.getResultSet();
            if (resultSet.next()) {
                //System.out.println("PERM " + resultSet.getString("p_name"));
                hasPermission = true;
            }

        } catch (SQLException throwables) {
            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();

        }
        return hasPermission;
    }

    public JsonObject approveUser(JsonObject data) {
        String user_id = data.getString("user_id");
        String actionBy = data.getString("user");

        String sql = "UPDATE users SET status = '1', updated_at = GETDATE() , verifier_id = ? WHERE id = ? ";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, user_id);
            preparedStatement.setString(2, actionBy);
            preparedStatement.executeUpdate();

            data
                    .put("responseCode", SUCCESS_CODE)
                    .put("responseDescription", "Success! ");

        } catch (SQLException throwables) {
            data
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! ");

            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }

        return data;
    }

    public JsonObject fetchRoleDetails(String searchColumn, String searchWord) {
        JsonObject res = new JsonObject();
        String sql = "SELECT Id,Name,CreatedAt,UpdatedAt FROM Roles "
                + "  WHERE " + searchColumn + " = ? ";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

//        JsonArray array = new JsonArray();
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, searchWord);
            preparedStatement.execute();

            ResultSet resultSet = preparedStatement.getResultSet();
            if (resultSet.next()) {
                JsonObject obj = new JsonObject();
                res
                        .put("successIndicator", true)
                        .put("Id", resultSet.getString("Id"))
                        .put("Name", resultSet.getString("Name"))
                        .put("CreatedAt", resultSet.getString("CreatedAt"))
                        .put("UpdatedAt", resultSet.getString("UpdatedAt"));
//                array.add(obj);
            } else {
                res.put("successIndicator", false);
            }

        } catch (SQLException throwables) {
            res.put("successIndicator", false);

            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }
//        res.put("data", array);
        return res;
    }

    public JsonObject fetchRoles() {
        JsonObject res = new JsonObject();
        String sql = "SELECT * FROM Roles ";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        JsonArray array = new JsonArray();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.execute();

            ResultSet resultSet = preparedStatement.getResultSet();
            while (resultSet.next()) {
                JsonObject obj = new JsonObject();
                obj
                        .put("Id", resultSet.getString("Id"))
                        .put("RoleName", resultSet.getString("Name"))
                        .put("CreatedAt", resultSet.getString("CreatedAt"))
                        .put("UpdatedAt", resultSet.getString("UpdatedAt"));
                array.add(obj);
            }

        } catch (SQLException throwables) {

            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }
        res.put("data", array);
        return res;
    }

    public JsonObject fetchPermissions() {
        JsonObject res = new JsonObject();
        String sql = "SELECT [Id],[Name],[CreatedAt],[UpdatedAt] FROM Permissions ";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        JsonArray array = new JsonArray();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.execute();

            ResultSet resultSet = preparedStatement.getResultSet();
            while (resultSet.next()) {
                JsonObject obj = new JsonObject();
                obj
                        .put("Id", resultSet.getString("Id"))
                        .put("PermissionName", resultSet.getString("Name"))
                        .put("CreatedAt", resultSet.getString("CreatedAt"))
                        .put("UpdatedAt", resultSet.getString("UpdatedAt"));
                array.add(obj);
            }

        } catch (SQLException throwables) {

            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }
        res.put("data", array);
        return res;
    }

    public JsonArray fetchRolePermissionsArray(String searchColumn, String searchWord) {
        String sql = "SELECT TOP (1000) rp.PermissionId ,rp.RoleId,\n"
                + "  r.Name,r.CreatedAt,r.UpdatedAt,\n"
                + "  p.Name AS p_name,p.CreatedAt AS p_created_at,p.UpdatedAt AS p_updated_at\n"
                + "FROM [dbo].[RoleHasPermissions] rp\n"
                + "RIGHT JOIN [roles] r ON r.Id = rp.RoleId\n"
                + "RIGHT JOIN [permissions] p ON p.Id = rp.PermissionId"
                + " WHERE " + searchColumn + " = ?";

        JsonArray array = new JsonArray();

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, searchWord);
            preparedStatement.execute();

            ResultSet resultSet = preparedStatement.getResultSet();
            while (resultSet.next()) {

                array.add(resultSet.getString("p_name"));
            }

        } catch (SQLException throwables) {

            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }

        return array;

    }

    public JsonObject fetchRolePermissions(String searchColumn, String searchWord) {
        JsonObject res = new JsonObject();
        String sql = "SELECT TOP (1000) rp.PermissionId ,rp.RoleId,\n"
                + "  r.Name,r.CreatedAt,r.UpdatedAt,\n"
                + "  p.Name AS p_name,p.CreatedAt AS p_created_at,p.UpdatedAt AS p_updated_at\n"
                + "FROM RoleHasPermissions rp\n"
                + "RIGHT JOIN Roles r ON r.id = rp.RoleId\n"
                + "RIGHT JOIN Permissions p ON p.id = rp.PermissionId"
                + " WHERE " + searchColumn + " = ?";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        JsonArray array = new JsonArray();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, searchWord);
            preparedStatement.execute();

            ResultSet resultSet = preparedStatement.getResultSet();
            while (resultSet.next()) {
                JsonObject obj = new JsonObject();
                obj
                        .put("roleId", resultSet.getString("RoleId"))
                        .put("roleName", resultSet.getString("Name"))
//                        .put("role_description", resultSet.getString("description"))
                        .put("roleCreatedAt", resultSet.getString("CreatedAt"))
                        .put("roleUpdatedAt", resultSet.getString("UpdatedAt"))
                        .put("permissionId", resultSet.getString("PermissionId"))
                        .put("permissionName", resultSet.getString("p_name"))
//                        .put("permission_description", resultSet.getString("p_description"))
                        .put("permissionCreatedAt", resultSet.getString("p_created_at"))
                        .put("permissionUpdatedAt", resultSet.getString("p_updated_at"));
                array.add(obj);
            }

        } catch (SQLException throwables) {

            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }
        res.put("data", array);
        return res;
    }

    public JsonObject addRolePermissions(JsonObject data) {
        JsonObject res = new JsonObject();
        String name = data.getString("Name");
        JsonArray permissions = data.getJsonArray("Permissions");
        String actionBy = data.getString("user");

        String insertRoles = "INSERT INTO roles(Name) VALUES(?)";
        String insertRolePermission = "INSERT INTO RoleHasPermissions(PermissionId,RoleId) VALUES(?,?)";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        JsonArray array = new JsonArray();

        try (PreparedStatement preparedStatement = connection.prepareStatement(insertRoles, PreparedStatement.RETURN_GENERATED_KEYS);
                PreparedStatement preparedStatement1 = connection.prepareStatement(insertRolePermission)) {
            preparedStatement.setString(1, name);
            preparedStatement.executeUpdate();

            ResultSet resultSet = preparedStatement.getGeneratedKeys();
            if (resultSet.next()) {
                int role_id = resultSet.getInt(1);
                for (byte x = 0; x < permissions.size(); x++) {
                    preparedStatement1.setInt(1, Integer.parseInt(permissions.getString(x)));
                    preparedStatement1.setInt(2, role_id);
                    preparedStatement1.addBatch();
                }
                int[] insertedRows = preparedStatement1.executeBatch();
                //System.out.println("INSERTED " + Arrays.toString(insertedRows));
            }

            res
                    .put("responseCode", SUCCESS_CODE)
                    .put("responseDescription", "Success! ");

        } catch (SQLException throwables) {
            res
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Failed Execution to add role and permissions");

            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }
//        res.put("data", array);
        return res;
    }

    public JsonObject updateRolePermissions(JsonObject data) {
        JsonObject res = new JsonObject();
        String roleId = data.getString("roleId");
        JsonArray permissions = data.getJsonArray("permissions");

        JsonObject roleDetails = fetchRoleDetails("[Id]", roleId);
        //System.out.println(roleDetails);
        if (!roleDetails.getBoolean("successIndicator")) {
            res
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Unable to fetch role details");
            return res;
        }
        int role_id = Integer.parseInt(roleDetails.getString("Id"));

//        String insertRoles = "INSERT INTO roles([name],[description],[creator_id]) VALUES(?,?,?)";
        String deleteRolesPermissions = "DELETE FROM RoleHasPermissions WHERE RoleId = ?";
        String insertRolePermission = "INSERT INTO RoleHasPermissions(PermissionId,RoleId)  VALUES(?,?)";

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

//        JsonArray array = new JsonArray();
        try (PreparedStatement preparedStatement = connection.prepareStatement(deleteRolesPermissions);
                PreparedStatement preparedStatement1 = connection.prepareStatement(insertRolePermission)) {
            preparedStatement.setInt(1, role_id);
            preparedStatement.executeUpdate();

            for (byte x = 0; x < permissions.size(); x++) {
                preparedStatement1.setInt(1, permissions.getInteger(x));
                preparedStatement1.setInt(2, role_id);
                preparedStatement1.addBatch();
            }
            int[] insertedRows = preparedStatement1.executeBatch();
            //System.out.println("INSERTED " + Arrays.toString(insertedRows));

            res
                    .put("responseCode", SUCCESS_CODE)
                    .put("responseDescription", "Success! ");

        } catch (SQLException throwables) {
            res
                    .put("responseCode", ERROR_CODE)
                    .put("responseDescription", "Error! Failed Execution to add role and permissions");

            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        } finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
            dbConnection.closeConn();
        }
//        res.put("data", array);
        return res;
    }

}
