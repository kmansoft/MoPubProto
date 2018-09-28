package org.kman.test.mopubproto;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.mopub.nativeads.ViewBinder;

import org.kman.test.mopubproto.mopubwrappers.AdHolder;
import org.kman.test.mopubproto.mopubwrappers.SdkInitializer;
import org.kman.test.mopubproto.util.MyLog;

public class MainActivity extends Activity implements SdkInitializer.OnSdkInitDoneListener, AdHolder
		.OnAdHolderStateChangeListener {

	private static final String TAG = "MainActivity";

	// This is "guaranteed fill" test ad ID from MoPub SDK sample
	//
	// https://github.com/mopub/mopub-android-sdk/blob/master/mopub-sample/res/values/strings.xml
	//
	private static final String AD_ID_FOR_INIT = "11a17b188668469fb0412708c3d16813";
	private static final String AD_ID_FOR_LOAD = "11a17b188668469fb0412708c3d16813";

	static class NonConfigData {
		SdkInitializer sdkInit;
		AdHolder adHolder;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		MyLog.i(TAG, "onCreate");

		mActivitySequenceNumber = ++gActivitySequenceNumber;

		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		mListContainer = findViewById(R.id.mopub_ad_list_container);

		mRebindAdView = findViewById(R.id.ad_view_rebind);
		mRebindAdView.setOnClickListener(this::rebindAdView);

		// Just testing, also making sure it's not removed by ProGuard
		final Context context = getApplicationContext();
		AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
			try {
				final AdvertisingIdClient.Info info = AdvertisingIdClient.getAdvertisingIdInfo(context);
				Log.i("MainActivity", "id = " + info);
			} catch (Exception ignore) {
			}
		});

		// Maybe we have MoPub data objects already?
		final Object nonConfigObject = getLastNonConfigurationInstance();
		if (nonConfigObject instanceof NonConfigData) {
			final NonConfigData nonConfigData = (NonConfigData) nonConfigObject;
			mSdkInitializer = nonConfigData.sdkInit;
			mAdHolder = nonConfigData.adHolder;
		}

		// Start MoPub SDK init
		if (mSdkInitializer == null) {
			mSdkInitializer = new SdkInitializer(this, AD_ID_FOR_INIT);
		}

		mSdkInitializer.setListener(this);
		mSdkInitializer.start();
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		final NonConfigData data = new NonConfigData();

		if (mSdkInitializer != null) {
			data.sdkInit = mSdkInitializer;

			mSdkInitializer.setListener(null);
			mSdkInitializer = null;
		}

		if (mAdHolder != null) {
			data.adHolder = mAdHolder;

			mAdHolder.setListener(null);
			mAdHolder = null;
		}

		return data;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		mActivityDestroyed = true;

		if (mSdkInitializer != null) {
			mSdkInitializer.setListener(null);
			mSdkInitializer = null;
		}

		if (mAdHolder != null) {
			mAdHolder.setListener(null);
			mAdHolder.onActivityDestroy();
			mAdHolder = null;
		}
	}

	@Override
	public void onSdkInitDone() {
		MyLog.i(TAG, "MoPub SDK initialization done");

		startAdRequest();
	}

	@Override
	public void onAdHolderStateChange(AdHolder holder) {
		updateAdState();
	}

	private void startAdRequest() {
		if (mAdHolder == null) {
			final ViewBinder binder = new ViewBinder.Builder(R.layout.native_ad_list_item).mainImageId(R.id
					.native_main_image).iconImageId(R.id.native_icon_image).titleId(R.id.native_title).textId(R.id
					.native_text).privacyInformationIconImageId(R.id.native_privacy_information_icon_image).build();

			mAdHolder = new AdHolder(this, AD_ID_FOR_LOAD, binder);
		}

		mAdHolder.setListener(this);
		mAdHolder.start();

		final TextView adInfoText = findViewById(R.id.ad_info_text);
		adInfoText.setText(getString(R.string.ad_status_loading, mAdHolder.getAdUnitId()));
	}

	private void updateAdState() {
		final TextView adInfoText = findViewById(R.id.ad_info_text);
		final String adUnitId = mAdHolder.getAdUnitId();

		final AdHolder.State state = mAdHolder.getState();
		final String message;
		switch (state) {
		default:
		case None:
			message = null;
			break;
		case Loading:
			message = getString(R.string.ad_status_loading, adUnitId);
			break;
		case Loaded:
			message = getString(R.string.ad_status_loaded, adUnitId);
			break;
		case Error:
			message = getString(R.string.ad_status_error, adUnitId);
			break;
		}

		adInfoText.setText(message);

		switch (state) {
		case Loaded:
			mRebindAdView.setEnabled(true);
			break;
		default:
			mRebindAdView.setEnabled(false);
			break;
		}

		if (state == AdHolder.State.Loaded) {
			// Loaded, show the ad
			mAdView0 = mAdHolder.getView(this, mAdView0, mListContainer);

			if (mAdView0.getParent() == null) {
				mListContainer.addView(mAdView0, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams
						.WRAP_CONTENT);
			}
		} else if (mAdView0 != null) {
			// Not loaded, nothing to show
			final ViewParent parent = mAdView0.getParent();
			if (parent instanceof ViewGroup) {
				((ViewGroup) parent).removeView(mAdView0);
			}
			mAdView0 = null;
		}
	}

	private void rebindAdView(View view) {
		if (mAdView0 != null && mAdHolder != null && mAdHolder.getState() == AdHolder.State.Loaded) {
			updateAdState();
		}
	}

	// For debugging - so we can see in Memory Profiler which is which
	private int mActivitySequenceNumber;
	private boolean mActivityDestroyed;

	private static int gActivitySequenceNumber;

	// Views
	private ViewGroup mListContainer;
	private View mAdView0;
	private Button mRebindAdView;

	// MoPub wrapper / utility objects
	private SdkInitializer mSdkInitializer;
	private AdHolder mAdHolder;
}
