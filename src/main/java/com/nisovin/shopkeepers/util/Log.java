package com.nisovin.shopkeepers.util;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.nisovin.shopkeepers.Settings;

public final class Log {

	private Log() {
	}

	// Gets set early on plugin startup and then (ideally) never unset.
	// -> Volatile is therefore not expected to be required.
	private static Logger logger = null;

	public static void setLogger(Logger logger) {
		Log.logger = logger;
	}

	public static Logger getLogger() {
		return logger;
	}

	public static void info(String message) {
		getLogger().info(message);
	}

	public static void info(Supplier<String> msgSupplier) {
		getLogger().info(msgSupplier);
	}

	public static void debug(String message) {
		debug(null, message);
	}

	public static void debug(Supplier<String> msgSupplier) {
		debug(null, msgSupplier);
	}

	public static void debug(String debugOption, String message) {
		if (Settings.isDebugging(debugOption)) {
			info(message);
		}
	}

	public static void debug(String debugOption, Supplier<String> msgSupplier) {
		if (Settings.isDebugging(debugOption)) {
			info(msgSupplier);
		}
	}

	public static void warning(String message) {
		getLogger().warning(message);
	}

	public static void warning(Supplier<String> msgSupplier) {
		getLogger().warning(msgSupplier);
	}

	public static void warning(String message, Throwable throwable) {
		getLogger().log(Level.WARNING, message, throwable);
	}

	public static void warning(Throwable throwable, Supplier<String> msgSupplier) {
		getLogger().log(Level.WARNING, throwable, msgSupplier);
	}

	public static void severe(String message) {
		getLogger().severe(message);
	}

	public static void severe(Supplier<String> msgSupplier) {
		getLogger().severe(msgSupplier);
	}

	public static void severe(String message, Throwable throwable) {
		getLogger().log(Level.SEVERE, message, throwable);
	}

	public static void severe(Throwable throwable, Supplier<String> msgSupplier) {
		getLogger().log(Level.SEVERE, throwable, msgSupplier);
	}
}
