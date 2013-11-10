package com.libzter.ftpg;
public class FTPGConstants {	
	public static final int SERVICE_READY_CODE = 220;
	public static final String SERVICE_READY_TEXT = "Service ready for new user";
	public static final int USER_OK_CODE = 331;
	public static final String USER_OK_TEXT = "User name okay, need password";
	public static final int NEED_ACCOUNT_CODE = 332;
	public static final String NEED_ACCOUNT_TEXT = "Need accoutn for login.";
	public static final int SERVICE_NOT_AVAILABLE_CODE = 421;
	public static final String SERVICE_NOT_AVAILABLE_TEXT = "Service not available, closing control connection."; // reply to any command if shutdown is in progress
	public static final int CANNOT_OPEN_DATA_CODE = 425;
	public static final String CANNOT_OPEN_DATA_TEXT = "Can't open data connection";
	public static final int INVALID_USER_PASS_CODE = 430;
	public static final String INVALID_USER_PASS_TEXT = "Invalid username or password";
}
