/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tracking.alerts;

import com.tracking.vehicles.VehiclesAdaptor;
import com.tracking.vehicles.VehiclesUtil;
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
public class AlertsAdaptor extends AbstractVerticle {

    private Logging logger;
    private static final Logger LOG = LoggerFactory.getLogger(AlertsAdaptor.class);
    EventBus eventBus;

    @Override
    public void start(Future<Void> done) throws Exception {
        eventBus = vertx.eventBus();
        logger = new Logging();
        
        eventBus.consumer("FETCH_ALERTS", this::fetchAlerts);
        eventBus.consumer("CREATE_ALERTS", this::createAlert);
        eventBus.consumer("UPDATE_ALERT", this::updateAlert);
    }
    
    private void fetchAlerts(Message<JsonObject> message) {
        JsonObject data = message.body();
        JsonObject response = new JsonObject();
        
        vertx.executeBlocking(new Handler<Future<JsonArray>>() {
            @Override
            public void handle(Future<JsonArray> future) {
                AlertsUtil alertsUtil = new AlertsUtil();
                JsonArray results = alertsUtil.fetchAlerts();
                future.complete(results);
            }
        }, false, asyncResult -> {
            if (asyncResult.succeeded()) {
                JsonArray results = asyncResult.result();
                if (results.size() > 0) {
                    response.put("responseCode", "000")
                            .put("responseDescription", "Successfully fetched alerts.")
                            .put("data", results);
                } else {
                    response.put("responseCode", "999")
                            .put("responseDescription", "No alerts found.");
                }
            } else {
                response.put("responseCode", "999")
                        .put("responseDescription", "Execution failed.");
            }
            message.reply(response);
        });
    }
    
    private void createAlert(Message<JsonObject> message) {
        JsonObject data = message.body();
        AlertsUtil alertsUtil = new AlertsUtil();
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
                int result = alertsUtil.createAlert(data);
                future.complete(result);
            }
        }, false, asyncResult -> {
            if (asyncResult.succeeded()) {
                int result = asyncResult.result();
                if (result != 0) {
                    response.put("responseCode", "000")
                            .put("responseDescription", "Successfully created alert.")
                            .put("data", result);
                    vertx.eventBus().send("CREATE_LOGS", new JsonObject()
                            .put("LogType", message.address())
                            .put("UserName", userName)
                            .put("Source", "")
                            .put("Process", "CREATE")
                            .put("RecordID", String.valueOf(result))
                            .put("Data", data.encode()));
                } else {
                    response.put("responseCode", "999")
                            .put("responseDescription", "Failed to create alert.");
                }
            } else {
                response.put("responseCode", "999")
                        .put("responseDescription", "Execution failed.");
            }
            message.reply(response);
        });
    }

    private void updateAlert(Message<JsonObject> message) {
        JsonObject data = message.body();
        AlertsUtil alertsUtil = new AlertsUtil();
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
                int result = alertsUtil.updateAlerts(data);
                future.complete(result);
            }
        }, false, asyncResult -> {
            if (asyncResult.succeeded()) {
                int result = asyncResult.result();
                if (result > 0) {
                    response.put("responseCode", "000")
                            .put("responseDescription", "Successfully updated alert.")
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
