/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tracking.vehicles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import log.Logging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Calvin
 */
public class VehiclesAdaptor extends AbstractVerticle {

    private Logging logger;
    private static final Logger LOG = LoggerFactory.getLogger(VehiclesAdaptor.class);
    EventBus eventBus;

    @Override
    public void start(Future<Void> done) throws Exception {
        eventBus = vertx.eventBus();
        logger = new Logging();

        eventBus.consumer("CREATE_VEHICLE", this::createVehicle);
        eventBus.consumer("FETCH_VEHICLES", this::fetchVehicles);
        eventBus.consumer("FETCH_VEHICLE_BY_ID", this::fetchVehicleById);
        eventBus.consumer("UPDATE_VEHICLE", this::updateVehicle);

        eventBus.consumer("CREATE_VEHICLE_TYPE", this::createVehicleType);
        eventBus.consumer("FETCH_VEHICLE_TYPES", this::fetchVehicleTypes);
        eventBus.consumer("FETCH_VEHICLE_TYPE_BY_ID", this::fetchVehicleTypeById);
        eventBus.consumer("UPDATE_VEHICLE_TYPE", this::updateVehicleType);
    }

    private void createVehicle(Message<JsonObject> message) {
        JsonObject data = message.body();
        VehiclesUtil vehicleUtil = new VehiclesUtil();
        JsonObject response = new JsonObject();

        MultiMap headers = message.headers();
        if (headers.isEmpty()) {
            //System.out.println("empty Header");
            message.fail(666, "Unauthenticated User");
            return;
        }
        String user = headers.get("user");
        String userName = headers.get("user_name");
//        boolean hasPermission = new UserUtil().checkUserHasPermission("u.id", user, "view_loans");
//        if (!hasPermission) {
//            data
//                    .put("responseCode", "999")
//                    .put("responseDescription", "Error! Unauthorised permission");
//            message.reply(data);
//            return;
//        }

        vertx.executeBlocking(new Handler<Future<Integer>>() {
            @Override
            public void handle(Future<Integer> future) {
                int result = vehicleUtil.createVehicle(data);
                future.complete(result);
            }
        }, false, asyncResult -> {
            if (asyncResult.succeeded()) {
                int result = asyncResult.result();
                if (result != 0) {
                    response.put("responseCode", "000")
                            .put("responseDescription", "Successfully created vehicle.")
                            .put("data", result);
                    vertx.eventBus().send("CREATE_LOGS", new JsonObject()
                            .put("LogType", message.address())
                            .put("UserName", userName)
                            .put("Source", "")
                            .put("Process", "CREATE")
                            .put("FolderNo", "")
                            .put("RecordID", String.valueOf(result))
                            .put("Data", data.encode()));
                } else {
                    response.put("responseCode", "999")
                            .put("responseDescription", "Failed to create vehicle.");
                }
            } else {
                response.put("responseCode", "999")
                        .put("responseDescription", "Execution failed.");
            }
            message.reply(response);
        });
    }

    private void fetchVehicles(Message<JsonObject> message) {
        JsonObject data = message.body();
        JsonObject response = new JsonObject();
        
        vertx.executeBlocking(new Handler<Future<JsonArray>>() {
            @Override
            public void handle(Future<JsonArray> future) {
                VehiclesUtil vehiclesUtil = new VehiclesUtil();
                JsonArray results = vehiclesUtil.fetchVehicles();
                future.complete(results);
            }
        }, false, asyncResult -> {
            if (asyncResult.succeeded()) {
                JsonArray results = asyncResult.result();
                if (results.size() > 0) {
                    response.put("responseCode", "000")
                            .put("responseDescription", "Successfully fetched vehicles.")
                            .put("data", results);
                } else {
                    response.put("responseCode", "999")
                            .put("responseDescription", "No vehicles found.");
                }
            } else {
                response.put("responseCode", "999")
                        .put("responseDescription", "Execution failed.");
            }
            message.reply(response);
        });
    }

    private void fetchVehicleById(Message<JsonObject> message) {
        JsonObject data = message.body();
        JsonObject response = new JsonObject();

        if (!data.containsKey("Id")) {
            response.put("responseCode", "999")
                    .put("responseDescription", "Missing required parameter: Id");
            message.reply(response);
            return;
        }

        String id = data.getString("Id");

        vertx.executeBlocking(promise -> {
            VehiclesUtil vehiclesUtil = new VehiclesUtil();
            JsonObject loanProduct = vehiclesUtil.fetchVehiclesById(id);
            promise.complete(loanProduct);
        }, false, res -> {
            if (res.succeeded()) {
                JsonObject vehicle = (JsonObject) res.result();

                if (!vehicle.isEmpty()) {
                    response.put("responseCode", "000")
                            .put("responseDescription", "Vehicle fetched successfully.")
                            .put("data", vehicle);
                } else {
                    response.put("responseCode", "999")
                            .put("responseDescription", "Vehicle not found.");
                }
            } else {
                response.put("responseCode", "999")
                        .put("responseDescription", "Execution failed.");
            }

            message.reply(response);
        });
    }

    private void updateVehicle(Message<JsonObject> message) {
        JsonObject data = message.body();
        VehiclesUtil vehiclesUtil = new VehiclesUtil();
        JsonObject response = new JsonObject();

        MultiMap headers = message.headers();
        if (headers.isEmpty()) {
            message.fail(666, "Unauthenticated User");
            return;
        }

        String user = headers.get("user");
        String userName = headers.get("user_name");

        if (!data.containsKey("Id")) {
            response.put("responseCode", "999")
                    .put("responseDescription", "Missing ID for update.");
            message.reply(response);
            return;
        }

        vertx.executeBlocking(new Handler<Future<Integer>>() {
            @Override
            public void handle(Future<Integer> future) {
                int result = vehiclesUtil.updateVehicles(data);
                future.complete(result);
            }
        }, false, asyncResult -> {
            if (asyncResult.succeeded()) {
                int result = asyncResult.result();
                if (result > 0) {
                    response.put("responseCode", "000")
                            .put("responseDescription", "Successfully updated vehicle.")
                            .put("updatedRows", String.valueOf(result));

                    vertx.eventBus().send("CREATE_LOGS", new JsonObject()
                            .put("LogType", message.address())
                            .put("UserName", userName)
                            .put("Source", "")
                            .put("Process", "UPDATE")
                            .put("FolderNo", "")
                            .put("RecordID", data.getInteger("id"))
                            .put("Data", data.encode()));
                } else {
                    response.put("responseCode", "999")
                            .put("responseDescription", "No rows updated. ID may not exist.");
                }
            } else {
                response.put("responseCode", "999")
                        .put("responseDescription", "Execution failed.");
            }
            message.reply(response);
        });
    }

    private void createVehicleType(Message<JsonObject> message) {
        JsonObject data = message.body();
        VehiclesUtil vehicleUtil = new VehiclesUtil();
        JsonObject response = new JsonObject();

        MultiMap headers = message.headers();
        if (headers.isEmpty()) {
            //System.out.println("empty Header");
            message.fail(666, "Unauthenticated User");
            return;
        }
        String user = headers.get("user");
        String userName = headers.get("user_name");
//        boolean hasPermission = new UserUtil().checkUserHasPermission("u.id", user, "view_loans");
//        if (!hasPermission) {
//            data
//                    .put("responseCode", "999")
//                    .put("responseDescription", "Error! Unauthorised permission");
//            message.reply(data);
//            return;
//        }

        vertx.executeBlocking(new Handler<Future<Integer>>() {
            @Override
            public void handle(Future<Integer> future) {
                int result = vehicleUtil.createVehicleType(data);
                future.complete(result);
            }
        }, false, asyncResult -> {
            if (asyncResult.succeeded()) {
                int result = asyncResult.result();
                if (result != 0) {
                    response.put("responseCode", "000")
                            .put("responseDescription", "Successfully created vehicle.")
                            .put("data", result);
                    vertx.eventBus().send("CREATE_LOGS", new JsonObject()
                            .put("LogType", message.address())
                            .put("UserName", userName)
                            .put("Source", "")
                            .put("Process", "CREATE")
                            .put("FolderNo", "")
                            .put("RecordID", String.valueOf(result))
                            .put("Data", data.encode()));
                } else {
                    response.put("responseCode", "999")
                            .put("responseDescription", "Failed to create vehicle.");
                }
            } else {
                response.put("responseCode", "999")
                        .put("responseDescription", "Execution failed.");
            }
            message.reply(response);
        });
    }

    private void fetchVehicleTypes(Message<JsonObject> message) {
        JsonObject data = message.body();
        JsonObject response = new JsonObject();

        vertx.executeBlocking(new Handler<Future<JsonArray>>() {
            @Override
            public void handle(Future<JsonArray> future) {
                VehiclesUtil vehiclesUtil = new VehiclesUtil();
                JsonArray results = vehiclesUtil.fetchVehicleTypes();
                future.complete(results);
            }
        }, false, asyncResult -> {
            if (asyncResult.succeeded()) {
                JsonArray results = asyncResult.result();
                if (results.size() > 0) {
                    response.put("responseCode", "000")
                            .put("responseDescription", "Successfully fetched vehicles.")
                            .put("data", results);
                } else {
                    response.put("responseCode", "999")
                            .put("responseDescription", "No vehicles found.");
                }
            } else {
                response.put("responseCode", "999")
                        .put("responseDescription", "Execution failed.");
            }
            message.reply(response);
        });
    }

    private void fetchVehicleTypeById(Message<JsonObject> message) {
        JsonObject data = message.body();
        JsonObject response = new JsonObject();

        if (!data.containsKey("Id")) {
            response.put("responseCode", "999")
                    .put("responseDescription", "Missing required parameter: Id");
            message.reply(response);
            return;
        }

        String id = data.getString("Id");

        vertx.executeBlocking(promise -> {
            VehiclesUtil vehiclesUtil = new VehiclesUtil();
            JsonObject loanProduct = vehiclesUtil.fetchVehicleTypesById(id);
            promise.complete(loanProduct);
        }, false, res -> {
            if (res.succeeded()) {
                JsonObject vehicle = (JsonObject) res.result();

                if (!vehicle.isEmpty()) {
                    response.put("responseCode", "000")
                            .put("responseDescription", "Vehicle fetched successfully.")
                            .put("data", vehicle);
                } else {
                    response.put("responseCode", "999")
                            .put("responseDescription", "Vehicle not found.");
                }
            } else {
                response.put("responseCode", "999")
                        .put("responseDescription", "Execution failed.");
            }

            message.reply(response);
        });
    }

    private void updateVehicleType(Message<JsonObject> message) {
        JsonObject data = message.body();
        VehiclesUtil vehiclesUtil = new VehiclesUtil();
        JsonObject response = new JsonObject();

        MultiMap headers = message.headers();
        if (headers.isEmpty()) {
            message.fail(666, "Unauthenticated User");
            return;
        }

        String user = headers.get("user");
        String userName = headers.get("user_name");

        if (!data.containsKey("Id")) {
            response.put("responseCode", "999")
                    .put("responseDescription", "Missing ID for update.");
            message.reply(response);
            return;
        }

        vertx.executeBlocking(new Handler<Future<Integer>>() {
            @Override
            public void handle(Future<Integer> future) {
                int result = vehiclesUtil.updateVehicleTypes(data);
                future.complete(result);
            }
        }, false, asyncResult -> {
            if (asyncResult.succeeded()) {
                int result = asyncResult.result();
                if (result > 0) {
                    response.put("responseCode", "000")
                            .put("responseDescription", "Successfully updated vehicle.")
                            .put("updatedRows", String.valueOf(result));

                    vertx.eventBus().send("CREATE_LOGS", new JsonObject()
                            .put("LogType", message.address())
                            .put("UserName", userName)
                            .put("Source", "")
                            .put("Process", "UPDATE")
                            .put("FolderNo", "")
                            .put("RecordID", data.getInteger("id"))
                            .put("Data", data.encode()));
                } else {
                    response.put("responseCode", "999")
                            .put("responseDescription", "No rows updated. ID may not exist.");
                }
            } else {
                response.put("responseCode", "999")
                        .put("responseDescription", "Execution failed.");
            }
            message.reply(response);
        });
    }
}
