package org.kman.test.mopubproto.util;

import android.util.Log;

import java.util.Locale;

public class MyLog {
	public static void i(String tag, String msg) {
		Log.i(tag, msg);
	}

	public static void i(String tag, String format, Object... args) {
		Log.i(tag, String.format(Locale.US, format, args));
	}

	public static void w(String tag, String msg, Throwable t) {
		Log.w(tag, msg, t);
	}
}
