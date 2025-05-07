/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tracking.nptrack;

import com.tracking.adaptors.EmailAdaptor;
import com.tracking.adaptors.Integrator;
import com.tracking.adaptors.SMSAdaptor;
import com.tracking.adaptors.Validation;
import com.tracking.user.UserAuth;
import com.tracking.util.Prop;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import log.ActivityLogVerticle;
import log.Logging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author demo
 */
public class EntryPoint extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(EntryPoint.class);

    public static Prop props;
    public static Logging logger;
    public static String LOGS_PATH;
    public static String DATABASE_DRIVER;
    public static String DATABASE_IP;
    public static String DATABASE_PORT;
    public static String DATABASE_NAME;
    public static String DATABASE_USER;
    public static String DATABASE_PASSWORD;
    public static String DATABASE_SERVER_TIME_ZONE;
    public static String SYSTEM_PORT;
    public static String SYSTEM_HOST;
    public static String ESB_ENDPOINT;
    public static String SMS_ENDPOINT;
    public static String DEFAULT_BASE_DIR;

    public static String SMTP_HOST;
    public static String SMTP_PORT;
    public static String SMTP_EMAIL;
    public static String SMTP_PASSWORD;

    // Hikari Setup
    static int MAX_POOL_SIZE = 3;
    static int MAX_IDLE_TIME = 4;
    static int WORKER_POOL_SIZE = 6;
    static int TIMEOUT_TIME = 90000;
    static int INITIAL_POOL_SIZE = 2;

    static {
        props = new Prop();
        logger = new Logging();
        LOGS_PATH = "";
        DATABASE_DRIVER = "";
        DATABASE_IP = "";
        DATABASE_PORT = "";
        DATABASE_NAME = "";
        DATABASE_USER = "";
        DATABASE_PASSWORD = "";
        DATABASE_SERVER_TIME_ZONE = "";
        SYSTEM_PORT = "";
        SYSTEM_HOST = "";
        ESB_ENDPOINT = "";
        SMS_ENDPOINT = "";
        DEFAULT_BASE_DIR = "";

        SMTP_HOST = "";
        SMTP_PORT = "";
        SMTP_PASSWORD = "";
        SMTP_EMAIL = "";
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        // instatiate Properties and Logging classes
        props = new Prop();
        logger = new Logging();

        // Get properties from property file
        LOGS_PATH = props.getLogsPath();
        DATABASE_DRIVER = props.getDATABASE_DRIVER();
        DATABASE_IP = props.getDATABASE_IP();
        DATABASE_PORT = props.getDATABASE_PORT();
        DATABASE_NAME = props.getDATABASE_NAME();
        DATABASE_USER = props.getDATABASE_USER();
        DATABASE_PASSWORD = props.getDATABASE_PASSWORD();
        DATABASE_SERVER_TIME_ZONE = props.getDATABASE_SERVER_TIME_ZONE();
        SYSTEM_PORT = props.getSYSTEM_PORT();
        SYSTEM_HOST = props.getSYSTEM_HOST();
        ESB_ENDPOINT = props.getESB_ENDPOINT();
        SMS_ENDPOINT = props.getSMS_ENDPOINT();

        SMTP_HOST = props.getEMAIL_HOST();
        SMTP_PORT = props.getEMAIL_PORT();
        SMTP_EMAIL = props.getEMAIL_SENDER();
        SMTP_PASSWORD = props.getEMAIL_PASSWORD();
        DEFAULT_BASE_DIR = props.getDEFAULT_BASE_DIR();

        // Deployment options
        DeploymentOptions options = new DeploymentOptions()
                .setInstances(5)
                .setWorker(true)
                .setWorkerPoolSize(50)
                .setHa(true);

        DeploymentOptions entryPointOptions = new DeploymentOptions()
                .setInstances(1)
                .setHa(true);

        // deploy Vertices Here 
        vertx.deployVerticle(EntryPoint.class.getName(), options);
        vertx.deployVerticle(Validation.class.getName(), options);
        vertx.deployVerticle(Integrator.class.getName(), options);
        vertx.deployVerticle(SMSAdaptor.class.getName(), options);
        vertx.deployVerticle(UserAuth.class.getName(), options);
        vertx.deployVerticle(EmailAdaptor.class.getName(), options);
        vertx.deployVerticle(ActivityLogVerticle.class.getName(), options);
    }

    @Override
    public void start(Future<Void> start_application) {
        EventBus eventBus = vertx.eventBus();
        int port = Integer.parseInt(SYSTEM_PORT);
        String host = SYSTEM_HOST;
        HttpServer ovHttpServer;

//        HttpServerOptions options = new HttpServerOptions()
//                .setSsl(true)
//                .setKeyCertOptions(
//                        new PemKeyCertOptions()
//                            .setCertPath("C:\\Java\\microfinance\\certs\\certificate.crt")
//                            .setKeyPath("C:\\Java\\microfinance\\certs\\private.key")
//                );
        ovHttpServer = vertx.createHttpServer();
        ovHttpServer.requestHandler(request -> {
            HttpServerResponse response = request.response();
            response.headers()
                    .add("Content-Type", "application/json")
                    .add("Access-Control-Allow-Origin", "*")
                    .add("Access-Control-Allow-Headers", "*")
                    .add("Access-Control-Allow-Methods", "*")
                    .add("Access-Control-Allow-Credentials", "true");
            String method = request.rawMethod();
            String path = request.path();
            String ip = request.remoteAddress().host();
            //System.out.println("Request IP Address "+ip);

            request.bodyHandler(bodyHandler -> {
                String body = bodyHandler.toString();
                JsonObject responseOBJ = new JsonObject();
                if (path.contains("/documents-upload") || path.contains("/workflow/documents")) {
                    // Forward the request or return a redirect
                    responseOBJ.put("responseCode", "302")
                            .put("responseDescription", "Redirected to document handler");
                    response.putHeader("Location", "/workflow/documents-upload")
                            .setStatusCode(302)
                            .end(responseOBJ.toString());
                    return;
                }
                if ("POST".equalsIgnoreCase(method)) {
                    JsonObject data = new JsonObject(body);
                    data.put("ip_address", ip);

                    logger.applicationLog(logger.logPreString() + "Channel Request  - " + data + "\n\n", "", 2);
                    if (path.endsWith("/nptrack/req")) {
                        try {
                            DeliveryOptions deliveryOptions = new DeliveryOptions()
                                    .setSendTimeout(TIMEOUT_TIME);
                            String processingCode = data.getString("processingCode");
                            // check validation
                            eventBus.send("VALIDATION", data, deliveryOptions, sendToBus -> {
                                if (sendToBus.succeeded()) {
                                    JsonObject resobj = (JsonObject) sendToBus.result().body();
                                    if ("pass".equalsIgnoreCase(resobj.getString("validation"))) {

                                        data.put("validation", resobj.getString("validation"));
                                        System.out.println("identifier " + data);
                                        eventBus.send(processingCode, data, deliveryOptions, sToBus -> {
                                            if (sToBus.succeeded()) {
                                                JsonObject resobject = (JsonObject) sToBus.result().body();
                                                //send response
                                                logger.applicationLog(logger.logPreString() + "Response to channel - " + resobject + "\n\n", "", 3);
                                                response.end(resobject.toString());
                                            } else {
                                                // error
                                                responseOBJ.put("responseCode", "999")
                                                        .put("responseDescription", processingCode + " failed")
                                                        .put("error_data", sToBus.cause().getLocalizedMessage());
                                                logger.applicationLog(logger.logPreString() + "Response to channel - " + responseOBJ + "\n\n", "", 5);
                                                response.end(responseOBJ.toString());

                                            }
                                        });

                                    } else {
                                        responseOBJ.put("responseCode", resobj.getString("response"))
                                                .put("responseDescription", resobj.getString("responseDescription"));
                                        logger.applicationLog(logger.logPreString() + "Response to channel - " + responseOBJ + "\n\n", "", 5);
                                        response.end(responseOBJ.toString());
                                    }
                                } else {
                                    // error
                                    responseOBJ.put("responseCode", "999")
                                            .put("responseDescription", "failed to process request")
                                            .put("error_data", sendToBus.cause().getLocalizedMessage());
                                    logger.applicationLog(logger.logPreString() + "Response to channel - " + responseOBJ + "\n\n", "", 5);
                                    response.end(responseOBJ.toString());
                                }
                            });

                        } catch (Exception ex) {
                            logger.applicationLog(logger.logPreString() + "Channel Request  - " + ex.getLocalizedMessage() + "\n\n", "", 5);
                            responseOBJ.put("responseCode", "901")
                                    .put("responseDescription", ex.getLocalizedMessage());
                            response.end(responseOBJ.toString());
                        }
                    } else {
                        // Unknown path
                        responseOBJ.put("responseCode", "404")
                                .put("responseDescription", "Invalid path");
                        response.end(responseOBJ.toString());
                    }
                } else {
                    // wrong request method
                    responseOBJ.put("responseCode", "901")
                            .put("responseDescription", "Bad Request");
                    response.end(responseOBJ.toString());
                }
            });
        });

        ovHttpServer.listen(port, resp -> {
            if (resp.succeeded()) {
                //System.out.println("System listening at " + host + ":" + port);
                LOG.info("System listening at " + host + ":" + port);
            } else {
                //System.out.println("System failed to start !!" + resp.failed());
            }
        });

//        vertx.setPeriodic(60000, periodic -> {
//            eventBus.send("LOAN_SERVICE",new JsonObject());
//        });
    }
}
