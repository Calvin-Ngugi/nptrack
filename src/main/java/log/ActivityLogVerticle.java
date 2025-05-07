package log;

import com.tracking.datasource.DBConnection;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ActivityLogVerticle extends AbstractVerticle {
    private Logging logger;
    private static final Logger LOG = LoggerFactory.getLogger(ActivityLogVerticle.class);
    EventBus eventBus;

    @Override
    public void start(Future<Void> done) throws Exception {
        //System.out.println("deploymentId ESBRouter = " + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        logger = new Logging();

        eventBus.consumer("ACTIVITY_LOG", this::createActivityLog);
        eventBus.consumer("CREATE_LOGS", this::createLogs);
    }

    private void createActivityLog(Message<JsonObject> message) {
        JsonObject data = message.body();
        String log_name = data.getString("logName");
        String description = data.getString("description");
        String subject_type = data.getString("subject_type");
        String subject_id = data.getString("subject_id");
        String causer_type = data.getString("user");
        String causer_id = data.getString("user");
        String properties = data.getString("properties");

        String sql = "INSERT INTO activity_log(log_name,description,subject_type,subject_id,causer_type,causer_id,properties,created_at) " +
                "VALUES(?,?,?,?,?,?,?,GETDATE())";
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1,log_name);
            preparedStatement.setString(2,description);
            preparedStatement.setString(3,subject_type);
            preparedStatement.setString(4, subject_id);
            preparedStatement.setString(5,causer_type);
            preparedStatement.setString(6,causer_id);
            preparedStatement.setString(7,properties);
            preparedStatement.executeUpdate();

        } catch (SQLException throwables) {
            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        }finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
        }
    }
    
    private void createLogs(Message<JsonObject> message){
        JsonObject data = message.body();
        String logData = data.getString("Data");
        String logType = data.getString("LogType");
        String userName = data.getString("UserName");
        String source = data.getString("Source");
        String process = data.getString("Process");
        String folderNo = data.getString("FolderNo");
        String recordId = data.getString("RecordID");

        String sql = "INSERT INTO idmsSysLog(LogDate,LogType,UserName,Source,Process,FolderNo,RecordID,Data) " +
                "VALUES(GETDATE(),?,?,?,?,?,?,?)";
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1,logType);
            preparedStatement.setString(2,userName);
            preparedStatement.setString(3,source);
            preparedStatement.setString(4,process);
            preparedStatement.setString(5,folderNo);
            preparedStatement.setString(6,recordId);
            preparedStatement.setString(7,logData);
            preparedStatement.executeUpdate();

        } catch (SQLException throwables) {
            throwables.printStackTrace();
            logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
        }finally {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.applicationLog(logger.logPreString() + "Error - " + throwables.getLocalizedMessage() + "\n\n", "", 6);
            }
        }
    }
}
