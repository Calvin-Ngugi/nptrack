/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tracking.util;

/**
 *
 * @author nathan
 */
public class StatusCode {

    public static String CREDIT_FLAG = "C";
    public static String DEBIT_FLAG = "D";
    public static String VAULT_TYPE = "VAULT";
    public static String TELLER_TYPE = "TELLER";

    public static String INCREASE = "I";
    public static String DECREASE = "D";

    public static String SUCCESS = "000";
    public static String G_ERROR = "999";
    public static int FETCH_STATUS = 1;
    public static int FAILED_TO_SENT = 2;
    public static int ACKNOWLEDGED = 3;
    public static int SUCCESS_CALLBACK = 4;
    public static int SUCCESS_CALLBACK_TXN_STATUS = 5;
    public static int TXN_CALLBACK_FAILED = 6;
    public static int CALLBACK_FAILED = 7;

}
