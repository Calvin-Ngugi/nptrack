package com.tracking.user;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import log.Logging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class UserAuth extends AbstractVerticle {

    private Logging logger;
    private static final Logger LOG = LoggerFactory.getLogger(UserAuth.class);
    EventBus eventBus;

    @Override
    public void start(Future<Void> done) throws Exception {
        //System.out.println("deploymentId ESBRouter = " + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        logger = new Logging();

        eventBus.consumer("900000", this::registration);
        eventBus.consumer("901000", this::login);
        eventBus.consumer("901500", this::otpVerification);
        eventBus.consumer("902000", this::forgotPassword);
        eventBus.consumer("903000", this::changePassword);
        eventBus.consumer("904000", this::fetchUsers);
        eventBus.consumer("904700", this::fetchUsersByEmail);
        
        eventBus.consumer("905000", this::fetchUser);
        eventBus.consumer("906000", this::activateUser);
        eventBus.consumer("906500", this::deactivateUser);
        eventBus.consumer("907000", this::logout);
        eventBus.consumer("908000", this::updateUser);
        eventBus.consumer("910000", this::createRoleAndItsPermissions);
        eventBus.consumer("911000", this::updateRolePermissions);
        eventBus.consumer("912000", this::fetchRoles);
        eventBus.consumer("913000", this::fetchPermissions);
        eventBus.consumer("914000", this::fetchRolePermissions);
        eventBus.consumer("999000", this::userValidator);
    }


    private void userValidator(Message<JsonObject> message){
        JsonObject data = message.body();
        String token = data.getString("token").trim();
        String serviceCode = data.getString("serviceCode").trim();

        byte[] tokenBytes =  Base64.getDecoder().decode(token.getBytes(StandardCharsets.UTF_8));
        String uuid = new String(tokenBytes, StandardCharsets.UTF_8);
        //System.out.println("TOKEN UUID \t "+uuid);

//        JsonObject jsonObject = new JsonObject(uuid);
//        //System.out.println("TOKEN Json \n"+jsonObject);


        UserUtil userUtil = new UserUtil();
        JsonObject userDetails = userUtil.fetchUserDetails("Uuid",uuid);
//        //System.out.println("\n USER "+userDetails);
        if (!userDetails.getBoolean("successIndicator")){
//            //System.out.println("invalid User Token");
            userDetails.clear();
            userDetails
                    .put("responseCode","999")
                    .put("responseDescription","Error! User has been deactivated ");
            message.reply(userDetails);
            return;
        }


        if (userDetails.getString("Status").equals("0")) {
            userDetails.clear();
            userDetails
                    .put("responseCode","999")
                    .put("responseDescription","Error! User has been deactivated ");
            message.reply(userDetails);
            return;
        }

        final JsonObject loginValidationDetails = userUtil.fetchLoginValidationDetails("UserId", uuid);
//        //System.out.println("Login Val "+loginValidationDetails);
        if ( !loginValidationDetails.getBoolean("successIndicator")) {
            userDetails.clear();
            userDetails
                    .put("responseCode","999")
                    .put("responseDescription","Error! User login details ");
            message.reply(userDetails);
            return;
        }

        if (loginValidationDetails.getString("ChangePassword").equals("1") && !serviceCode.equals("903000") && !serviceCode.equals("901500")) {
            userDetails.clear();
            userDetails
                    .put("responseCode","999")
                    .put("responseDescription","Error! Change Password to proceed");
            message.reply(userDetails);
            return;
        }

        if (loginValidationDetails.getString("OTPVerified").equals("0") && !serviceCode.equals("901500")) {
            userDetails.clear();
            userDetails
                    .put("responseCode","999")
                    .put("responseDescription","Error! OTP not verified to proceed");
            message.reply(userDetails);
            return;
        }

//        JsonArray usersBranchesDetails = userDetails.getJsonArray("userBranchesDetails");
//        JsonArray usersBranchesJsonArray = userDetails.getJsonArray("userBranches");
//
//        if (usersBranchesJsonArray.size() == 0) {
//            userDetails.clear();
//            userDetails
//                    .put("responseCode","999")
//                    .put("responseDescription","Error! User "+userDetails.getString("firstName")
//                            + userDetails.getString("lastName") +" has not been allocated a branch");
//            message.reply(userDetails);
//            return;
//        }
//
//        String userBranchesString = "";
//        for (int x = 0; x < usersBranchesJsonArray.size(); x++ ) {
//            userBranchesString = userBranchesString + "'" + usersBranchesJsonArray.getString(x) + "',";
//        }
//
//        if (userBranchesString.endsWith(",")) {
//            userBranchesString = userBranchesString.substring(0,userBranchesString.length() - 1);
//        }


        String user_fullname = userDetails.getString("FirstName") + " " +userDetails.getString("LastName");
        String userRole = userDetails.getString("Role");

        DeliveryOptions deliveryOptions = new DeliveryOptions()
                .addHeader("user", userDetails.getString("Id"))
                .addHeader("user_uuid", uuid)
                .addHeader("user_name", user_fullname)
                .addHeader("user_role", userRole);
//                .addHeader("user_branch_id", usersBranchesJsonArray.getString(0))
//                .addHeader("user_branch_name", userDetails.getString("branchName"))
//                .addHeader("user_branches", userBranchesString);

        data.remove("username");
        data.remove("password");
        data.remove("token");
        data.remove("processingCode");
        data.remove("serviceCode");
        eventBus.send(serviceCode,data,deliveryOptions, sendToBus -> {
            if (sendToBus.succeeded()){
                //System.out.println("SUCCEEDED");
                JsonObject resObj = (JsonObject) sendToBus.result().body();
                message.reply(resObj);
            } else {
                System.err.println("Failed");
                data.clear();
                data
                        .put("responseCode", "999")
                        .put("responseDescription", "Error! Failed to process service "+ sendToBus.result().address());
                message.reply(data);
//                message.fail(120,"Error! Failed to process service "+sendToBus.result().address());
            }
        });
//        message.reply(quickResponse);
    }

    private void registration(Message<JsonObject> message){
        JsonObject data = message.body();

        MultiMap headers = message.headers();
        if (headers.isEmpty()){
            //System.out.println("empty Header");
            message.fail(666,"Unauthenticated User");
            return;
        }
        String user = headers.get("user");
        String userName = headers.get("user_name");
        boolean hasPermission = new UserUtil().checkUserHasPermission("u.id",user,"create_users");
        if (!hasPermission) {
            data
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Unauthorised permission");
            message.reply(data);
            return;
        }

        data.put("user",user);

        JsonObject quickResponse = new UserUtil().registerUser(data);

        if (quickResponse.containsKey("emailBody")) {
            DeliveryOptions deliveryOptions = new DeliveryOptions()
                    .addHeader("emailRecipient", quickResponse.getString("emailRecipient"))
                    .addHeader("emailSubject", quickResponse.getString("emailSubject"))
                    .addHeader("emailBody", quickResponse.getString("emailBody"));

            eventBus.send("SEND_EMAIL", quickResponse,deliveryOptions);
            quickResponse.remove("emailRecipient");
            quickResponse.remove("emailSubject");
            quickResponse.remove("emailBody");
        }
        
        if (quickResponse.containsKey("msg")) {
            JsonObject smsObject = new JsonObject();
            smsObject
                    .put("phonenumber", quickResponse.getString("phonenumber"))
                    .put("msg", quickResponse.getString("msg"));
            
            eventBus.send("COMMUNICATION_ADAPTOR_BASIC", smsObject);
        }
        eventBus.send("CREATE_LOGS", new JsonObject()
                            .put("LogType", message.address())
                            .put("UserName", "")
                            .put("Source", "")
                            .put("Process", "User Registration")
                            .put("FolderNo", "")
                            .put("RecordID", "")
                            .put("Data", data.encode()));
        
        message.reply(quickResponse);
    }

    private void updateUser(Message<JsonObject> message){
        JsonObject data = message.body();

        MultiMap headers = message.headers();
        if (headers.isEmpty()){
            //System.out.println("empty Header");
            message.fail(666,"Unauthenticated User");
            return;
        }
        String user = headers.get("user");
        boolean hasPermission = new UserUtil().checkUserHasPermission("u.id",user,"manage_users");
        if (!hasPermission) {
            data
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Unauthorised permission");
            message.reply(data);
            return;
        }

        data.put("user",user);

        JsonObject quickResponse = new UserUtil().updateUser(data);
        message.reply(quickResponse);
    }

    private void login(Message<JsonObject> message){
        JsonObject data = message.body();
        JsonObject quickResponse = new UserUtil().login(data);
        if (quickResponse.getString("responseCode").equals("000")) {
            eventBus.send("COMMUNICATION_ADAPTOR_BASIC",quickResponse);
            quickResponse.remove("phonenumber");
            quickResponse.remove("msg");

            DeliveryOptions deliveryOptions = new DeliveryOptions()
                    .addHeader("emailRecipient", quickResponse.getString("emailRecipient"))
                    .addHeader("emailSubject", quickResponse.getString("emailSubject"))
                    .addHeader("emailBody", quickResponse.getString("emailBody"));

            eventBus.send("SEND_EMAIL", quickResponse,deliveryOptions);
            quickResponse.remove("emailRecipient");
            quickResponse.remove("emailSubject");
            quickResponse.remove("emailBody");
            eventBus.send("CREATE_LOGS", new JsonObject()
                            .put("LogType", message.address())
                            .put("UserName", data.getString("email"))
                            .put("Source", "")
                            .put("Process", "Login")
                            .put("FolderNo", "")
                            .put("RecordID", "")
                            .put("Data", data.encode()));
        }
        message.reply(quickResponse);
    }

    private void otpVerification(Message<JsonObject> message){
        JsonObject data = message.body();
        MultiMap headers = message.headers();
        if (headers.isEmpty()){
            //System.out.println("empty Header");
            message.fail(666,"Unauthenticated User");
            return;
        }
        String user = headers.get("user");
        String userName = headers.get("user_name");
        String user_uuid = headers.get("user_uuid");
        String user_role = headers.get("user_role");

        data.put("user_uuid",user_uuid);
        JsonObject quickResponse = new UserUtil().otpVerification(data);
        quickResponse.put("userId", user);
        quickResponse.put("user_uuid", user_uuid);
        quickResponse.put("user_role", user_role);
        message.reply(quickResponse);
        
        eventBus.send("CREATE_LOGS", new JsonObject()
                            .put("LogType", message.address())
                            .put("UserName", userName)
                            .put("Source", "")
                            .put("Process", "OTP Validation")
                            .put("FolderNo", "")
                            .put("RecordID", "")
                            .put("Data", data.encode()));

    }

    private void logout(Message<JsonObject> message){
        JsonObject data = message.body();
        JsonObject quickResponse = new UserUtil().logout(data);
        message.reply(quickResponse);
    }

    private void forgotPassword(Message<JsonObject> message){
        JsonObject data = message.body();
        JsonObject quickResponse = new UserUtil().forgotPassword(data);
        if (quickResponse.getString("responseCode").equals("000")) {
            eventBus.send("COMMUNICATION_ADAPTOR_BASIC",quickResponse);
            quickResponse.remove("phonenumber");
            quickResponse.remove("msg");
            
            DeliveryOptions deliveryOptions = new DeliveryOptions()
                    .addHeader("emailRecipient", quickResponse.getString("emailRecipient"))
                    .addHeader("emailSubject", quickResponse.getString("emailSubject"))
                    .addHeader("emailBody", quickResponse.getString("emailBody"));
            eventBus.send("SEND_EMAIL",quickResponse,deliveryOptions);
            quickResponse.remove("emailRecipient");
            quickResponse.remove("emailSubject");
            quickResponse.remove("emailBody");
            eventBus.send("CREATE_LOGS", new JsonObject()
                            .put("LogType", message.address())
                            .put("UserName", data.getString("email"))
                            .put("Source", "")
                            .put("Process", "Forgot Password")
                            .put("FolderNo", "")
                            .put("RecordID", "")
                            .put("Data", data.encode()));
        }
        message.reply(quickResponse);
    }

    private void changePassword(Message<JsonObject> message){
        JsonObject data = message.body();
        MultiMap headers = message.headers();
        if (headers.isEmpty()){
            //System.out.println("empty Header");
            message.fail(666,"Unauthenticated User");
            return;
        }
        String user = headers.get("user");
        String userName = headers.get("user_name");

        data.put("user",user);
        JsonObject quickResponse = new UserUtil().changePassword(data);
        if (quickResponse.getString("responseCode").equals("000")) {
            DeliveryOptions deliveryOptions = new DeliveryOptions()
                    .addHeader("emailRecipient", quickResponse.getString("emailRecipient"))
                    .addHeader("emailSubject", quickResponse.getString("emailSubject"))
                    .addHeader("emailBody", quickResponse.getString("emailBody"));
            eventBus.send("SEND_EMAIL",quickResponse,deliveryOptions);
            quickResponse.remove("emailRecipient");
            quickResponse.remove("emailSubject");
            quickResponse.remove("emailBody");
            eventBus.send("CREATE_LOGS", new JsonObject()
                            .put("LogType", message.address())
                            .put("UserName", userName)
                            .put("Source", "")
                            .put("Process", "Change Password")
                            .put("FolderNo", "")
                            .put("RecordID", "")
                            .put("Data", data.encode()));
        }
        message.reply(quickResponse);
    }

    private void activateUser(Message<JsonObject> message){
        JsonObject data = message.body();
        MultiMap headers = message.headers();
        if (headers.isEmpty()){
            //System.out.println("empty Header");
            message.fail(666,"Unauthenticated User");
            return;
        }
        String user = headers.get("user");
        String userName = headers.get("user_name");
        boolean hasPermission = new UserUtil().checkUserHasPermission("u.id",user,"manage_users");
        if (!hasPermission) {
            data
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Unauthorised permission");
            message.reply(data);
            return;
        }

        data.put("user", user);
        data.put("status", "1");

        JsonObject quickResponse = new UserUtil().activateDeactivateUser(data);
        eventBus.send("CREATE_LOGS", new JsonObject()
                            .put("LogType", message.address())
                            .put("UserName", userName)
                            .put("Source", "")
                            .put("Process", "Activate User")
                            .put("FolderNo", "")
                            .put("RecordID", "")
                            .put("Data", data.encode()));
        message.reply(quickResponse);
    }

    private void deactivateUser(Message<JsonObject> message){
        JsonObject data = message.body();
        MultiMap headers = message.headers();
        if (headers.isEmpty()){
            //System.out.println("empty Header");
            message.fail(666,"Unauthenticated User");
            return;
        }
        String user = headers.get("user");
        String userName = headers.get("user_name");
        boolean hasPermission = new UserUtil().checkUserHasPermission("u.id",user,"manage_users");
        if (!hasPermission) {
            data
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Unauthorised permission");
            message.reply(data);
            return;
        }

        data.put("user", user);
        data.put("status", "0");

        JsonObject quickResponse = new UserUtil().activateDeactivateUser(data);
        eventBus.send("CREATE_LOGS", new JsonObject()
                            .put("LogType", message.address())
                            .put("UserName", userName)
                            .put("Source", "")
                            .put("Process", "Deactivate User")
                            .put("FolderNo", "")
                            .put("RecordID", "")
                            .put("Data", data.encode()));
        message.reply(quickResponse);
    }
    
    private void fetchUsersByEmail(Message<JsonObject> message) {
        JsonObject data = message.body();
        MultiMap headers = message.headers();
        if (headers.isEmpty()){
            //System.out.println("empty Header");
            message.fail(666,"Unauthenticated User");
            return;
        }
        String user = headers.get("user");
        String user_branches = headers.get("user_branches");
        boolean hasPermission = new UserUtil().checkUserHasPermission("u.id",user,"view_users");
        if (!hasPermission) {
            data
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Unauthorised permission");
            message.reply(data);
            return;
        }

        String email = data.getString("email");
        data.clear();

        JsonObject result = new UserUtil().fetchUserDetails("u.email",email);
        if (result.getJsonArray("data").size() == 0) {
            result
                    .put("responseCode", "999")
                    .put("responseDescription", "Error");
        } else {
            result
                    .put("responseCode", "000")
                    .put("responseDescription", "Success");
        }
        message.reply(result);
    }

    private void fetchUsers(Message<JsonObject> message){
        JsonObject data = message.body();
        MultiMap headers = message.headers();
        if (headers.isEmpty()){
            //System.out.println("empty Header");
            message.fail(666,"Unauthenticated User");
            return;
        }
        String user = headers.get("user");
        String user_branches = headers.get("user_branches");
        boolean hasPermission = new UserUtil().checkUserHasPermission("u.id",user,"view_users");
        if (!hasPermission) {
            data
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Unauthorised permission");
            message.reply(data);
            return;
        }

        data.clear();

//        JsonArray array = new UserUtil().fetchUsersWithUsersBranchesTable("ub.BranchId IN ("+user_branches+") AND 1","1");
//        if (array.size() == 0) {
//            data
//                    .put("responseCode", "999")
//                    .put("responseDescription", "Error");
//        } else {
//            data
//                    .put("responseCode", "000")
//                    .put("responseDescription", "Success");
//        }
//        data.put("data",array);
        // message.reply(data);

        JsonObject result = new UserUtil().fetchUsers("1","1");
        if (result.getJsonArray("data").size() == 0) {
            result
                    .put("responseCode", "999")
                    .put("responseDescription", "Error");
        } else {
            result
                    .put("responseCode", "000")
                    .put("responseDescription", "Success");
        }
        message.reply(result);
    }
    
    private void fetchUser(Message<JsonObject> message){
        JsonObject data = message.body();
        String user_email = data.getString("email");
        JsonObject response = new JsonObject();

        MultiMap headers = message.headers();
        if (headers.isEmpty()){
            //System.out.println("empty Header");
            message.fail(666,"Unauthenticated User");
            return;
        }
        String user = headers.get("user");
        boolean hasPermission = new UserUtil().checkUserHasPermission("u.id",user,"view_users");
        if (!hasPermission) {
            data
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Unauthorised permission");
            message.reply(data);
            return;
        }

        JsonArray userDetails = new UserUtil().fetchUserDetails(user_email);
        if (userDetails.size() > 0) {
            response
                    .put("responseCode", "000")
                    .put("responseDescription", "Success")
                    .put("data", userDetails);
        } else {
            response
                    .put("responseCode", "999")
                    .put("responseDescription", "Error");
        }

        message.reply(response);
    }

    private void fetchRoles(Message<JsonObject> message){
        JsonObject data = message.body();
        MultiMap headers = message.headers();
        if (headers.isEmpty()){
            //System.out.println("empty Header");
            message.fail(666,"Unauthenticated User");
            return;
        }
        String user = headers.get("user");
        boolean hasPermission = new UserUtil().checkUserHasPermission("u.id",user,"view_roles");
        if (!hasPermission) {
            data
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Unauthorised permission");
            message.reply(data);
            return;
        }

        JsonObject response = new UserUtil().fetchRoles();
        if (response.getJsonArray("data").size() > 0 ) {
            response
                    .put("responseCode", "000")
                    .put("responseDescription", "Success");
        } else {
            response
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Failed to retrieve roles");
        }
        message.reply(response);
    }

    private void fetchPermissions(Message<JsonObject> message){
        JsonObject data = message.body();
        MultiMap headers = message.headers();
        if (headers.isEmpty()){
            //System.out.println("empty Header");
            message.fail(666,"Unauthenticated User");
            return;
        }
        String user = headers.get("user");
        boolean hasPermission = new UserUtil().checkUserHasPermission("u.id",user,"view_permissions");
        if (!hasPermission) {
            data
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Unauthorised permission");
            message.reply(data);
            return;
        }

        JsonObject response = new UserUtil().fetchPermissions();
        if (response.getJsonArray("data").size() > 0 ) {
            response
                    .put("responseCode", "000")
                    .put("responseDescription", "Success");
        } else {
            response
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Failed to retrieve permissions");
        }
        message.reply(response);
    }

    private void fetchRolePermissions(Message<JsonObject> message){
        JsonObject data = message.body();
        String role_id = data.getString("identifier");

        MultiMap headers = message.headers();
        if (headers.isEmpty()){
            //System.out.println("empty Header");
            message.fail(666,"Unauthenticated User");
            return;
        }
        String user = headers.get("user");
        boolean hasPermission = new UserUtil().checkUserHasPermission("u.id",user,"view_roles");
        if (!hasPermission) {
            data
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Unauthorised permission");
            message.reply(data);
            return;
        }

        JsonObject response = new UserUtil().fetchRolePermissions("rp.[role_id]",role_id);
        if (response.getJsonArray("data").size() > 0 ) {
            response
                    .put("responseCode", "000")
                    .put("responseDescription", "Success");
        } else {
            response
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Failed to retrieve permissions");
        }
        message.reply(response);
    }

    private void createRoleAndItsPermissions(Message<JsonObject> message){
        JsonObject data = message.body();

        MultiMap headers = message.headers();
        if (headers.isEmpty()){
            //System.out.println("empty Header");
            message.fail(666,"Unauthenticated User");
            return;
        }
        String user = headers.get("user");
        boolean hasPermission = new UserUtil().checkUserHasPermission("u.id",user,"view_users");
        if (!hasPermission) {
            data
                    .put("responseCode", "999")
                    .put("responseDescription", "Error! Unauthorised permission");
            message.reply(data);
            return;
        }

        JsonObject response = new UserUtil().addRolePermissions(data);
        message.reply(response);
    }

    private void updateRolePermissions(Message<JsonObject> message){
        JsonObject data = message.body();

        JsonObject response = new UserUtil().updateRolePermissions(data);
        message.reply(response);
    }
}
