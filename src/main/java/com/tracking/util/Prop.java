package com.tracking.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author nathan
 */
public final class Prop {

    private transient Properties props;
    private transient List<String> loadErrors;
    private final transient String error1 = "ERROR: %s is <= 0 or may not have been set";
    private final transient String error2 = "ERROR: %s may not have been set";
    private static final String PROPS_FILE = System.getProperty("user.dir") + File.separator + "appconfig" + File.separator + "configurations.properties";

    private transient String LOGS_PATH;
    private transient String DATABASE_DRIVER;
    private transient String DATABASE_IP;
    private transient String DATABASE_PORT;
    private transient String DATABASE_NAME;
    private transient String DATABASE_USER;
    private transient String DATABASE_PASSWORD;
    private transient String DATABASE_SERVER_TIME_ZONE;
    private transient String SYSTEM_PORT;
    private transient String SYSTEM_HOST;
    private transient String ESB_ENDPOINT;
    private transient String SMS_ENDPOINT;
    private transient String DEFAULT_BASE_DIR;

    // EMAIL
    private transient String EMAIL_SENDER;
    private transient String EMAIL_PASSWORD;
    private transient String EMAIL_PORT;
    private transient String EMAIL_HOST;

    /**
     * Instantiates a new Props.
     */
    public Prop() {
        loadProperties(PROPS_FILE);
    }

    private void loadProperties(final String propsFileName) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(propsFileName);
            props = new Properties();
            props.load(inputStream);

            Encryption ency = new Encryption();

//            //System.out.println("Serenity "+ B2CUtil.encryptInitiatorPassword("Serenity2022*#"));

            LOGS_PATH = readString("LOGS_PATH").trim();

            DATABASE_DRIVER = readString("DATABASE_DRIVER").trim();
            DATABASE_IP = readString("DATABASE_IP").trim();
            DATABASE_PORT = readString("DATABASE_PORT").trim();
            DATABASE_NAME = readString("DATABASE_NAME").trim();
            DATABASE_USER = readString("DATABASE_USER").trim();
            DATABASE_PASSWORD = ency.decrypt(readString("DATABASE_PASSWORD").trim());
//            DATABASE_PASSWORD = readString("DATABASE_PASSWORD").trim();
            DATABASE_SERVER_TIME_ZONE = readString("DATABASE_SERVER_TIME_ZONE").trim();
            SYSTEM_PORT = readString("SYSTEM_PORT").trim();
            SYSTEM_HOST = readString("SYSTEM_HOST").trim();
            ESB_ENDPOINT = readString("ESB_ENDPOINT").trim();
            SMS_ENDPOINT = readString("SMS_ENDPOINT").trim();
            DEFAULT_BASE_DIR = readString("DEFAULT_BASE_DIR").trim();

            EMAIL_HOST = readString("SMTP_HOST").trim();
            EMAIL_PORT = readString("SMTP_PORT").trim();
            EMAIL_PASSWORD = ency.decrypt(readString("EMAIL_PASSWORD")).trim();
            EMAIL_SENDER = ency.decrypt(readString("EMAIL_SENDER")).trim();


        } catch (IOException ex) {
            Logger.getLogger(Prop.class.getName()).log(Level.SEVERE, "ERROR: Failed to load properties file.\nCause: \n", ex);

        } catch (Exception ex) {
            Logger.getLogger(Prop.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                inputStream.close();
            } catch (IOException ex) {
                Logger.getLogger(Prop.class.getName()).log(Level.SEVERE, "ERROR: Failed to load properties file.\nCause: \n", ex);
            }
        }
    }

    /**
     * Read string string. - This function reads a String from the properties
     * file
     *
     * @param propertyName the property name
     * @return the string
     */
    public String readString(String propertyName) {
        String property = props.getProperty(propertyName);
        if (property.isEmpty()) {
            getLoadErrors().add(String.format(error2, propertyName));
        }
        return property;
    }

    /**
     * Read integer. - This function gets a String property from the properties
     * file and parses it into and INT.
     *
     * @param propertyName the property name
     * @return the integer
     */
    public int readInt(String propertyName) {
        int property = 0;
        String propertyString = props.getProperty(propertyName);
        if (propertyString.isEmpty()) {
            getLoadErrors().add(String.format(error1, propertyName));
        } else {
            property = Integer.parseInt(propertyString);
            if (property < 0) {
                getLoadErrors().add(String.format(error1,
                        propertyName));
            }
        }
        return property;
    }

    /**
     * Read float float. - This function gets a String property from the
     * properties file and parses it into and FLOAT.
     *
     * @param propertyName the property name
     * @return the float
     */
    public float readFloat(String propertyName) {
        float property = 0;
        String propertyString = props.getProperty(propertyName);
        if (propertyString.isEmpty()) {
            getLoadErrors().add(String.format(error1, propertyName));
        } else {
            property = Float.parseFloat(propertyString);
            if (property < 0) {
                getLoadErrors().add(String.format(error1,
                        propertyName));
            }
        }
        return property;
    }

    /**
     * Read double double. - This function gets a String property from the
     * properties file and parses it into and DOUBLE.
     *
     * @param propertyName the property name
     * @return the double
     */
    public double readDouble(String propertyName) {
        double property = 0.0;
        String propertyString = props.getProperty(propertyName);
        if (propertyString.isEmpty()) {
            getLoadErrors().add(String.format(error1, propertyName));
        } else {
            property = Double.parseDouble(propertyString);
            if (property < 0) {
                getLoadErrors().add(String.format(error1,
                        propertyName));
            }
        }
        return property;
    }

    /**
     * Gets load errors.
     *
     * @return the load errors
     */
    public List<String> getLoadErrors() {
        return loadErrors;
    }

    /**
     * Gets logs path.
     *
     * @return the logs path
     */
    public String getLogsPath() {
        return LOGS_PATH;
    }

    public String getDATABASE_DRIVER() {
        return DATABASE_DRIVER;
    }

    public String getDATABASE_IP() {
        return DATABASE_IP;
    }

    public String getDATABASE_PORT() {
        return DATABASE_PORT;
    }

    public String getDATABASE_NAME() {
        return DATABASE_NAME;
    }

    public String getDATABASE_USER() {
        return DATABASE_USER;
    }

    public String getDATABASE_PASSWORD() {
        return DATABASE_PASSWORD;
    }

    public String getDATABASE_SERVER_TIME_ZONE() {
        return DATABASE_SERVER_TIME_ZONE;
    }

    public String getSYSTEM_PORT() {
        return SYSTEM_PORT;
    }

    public String getSYSTEM_HOST() {
        return SYSTEM_HOST;
    }

    public String getESB_ENDPOINT() {
        return ESB_ENDPOINT;
    }

    public String getSMS_ENDPOINT() {
        return SMS_ENDPOINT;
    }

    public String getEMAIL_HOST() {
        return EMAIL_HOST;
    }

    public String getEMAIL_PORT() {
        return EMAIL_PORT;
    }

    public String getEMAIL_SENDER() {
        return EMAIL_SENDER;
    }

    public String getEMAIL_PASSWORD() {
        return EMAIL_PASSWORD;
    }
    
    public String getDEFAULT_BASE_DIR() {
        return DEFAULT_BASE_DIR;
    }

}
