package org.kman.test.mopubproto.mopubwrappers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.mopub.common.MoPub;
import com.mopub.common.SdkConfiguration;

public class SdkInitializer {

	public interface OnSdkInitDoneListener {
		void onSdkInitDone();
	}

	public SdkInitializer(Context context, String initUnitId) {
		mContext = context.getApplicationContext();
		mInitUnitId = initUnitId;
		mHandler = new Handler(Looper.getMainLooper());
	}

	public void setListener(OnSdkInitDoneListener listener) {
		mListener = listener;
		mIsListenerCalled = false;
	}

	public void start() {
		if (MoPub.isSdkInitialized()) {
			// Already initialized, post callback
			postCallListener();
		} else if (!mIsInitStarted) {
			// Not started yet
			mIsInitStarted = true;

			final SdkConfiguration config = new SdkConfiguration.Builder(mInitUnitId).build();
			MoPub.initializeSdk(mContext, config, this::postCallListener);
		}
	}

	private void postCallListener() {
		mHandler.post(this::callListener);
	}

	private void callListener() {
		if (mListener != null && !mIsListenerCalled) {
			mIsListenerCalled = true;

			mListener.onSdkInitDone();
		}
	}

	private final Context mContext;
	private final String mInitUnitId;
	private final Handler mHandler;

	private OnSdkInitDoneListener mListener;
	private boolean mIsListenerCalled;

	private boolean mIsInitStarted;
}
