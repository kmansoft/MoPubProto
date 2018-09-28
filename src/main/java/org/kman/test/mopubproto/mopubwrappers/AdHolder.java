package org.kman.test.mopubproto.mopubwrappers;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import com.mopub.nativeads.AdapterHelper;
import com.mopub.nativeads.MoPubNative;
import com.mopub.nativeads.MoPubStaticNativeAdRenderer;
import com.mopub.nativeads.NativeAd;
import com.mopub.nativeads.NativeErrorCode;
import com.mopub.nativeads.ViewBinder;

import org.kman.test.mopubproto.util.MyLog;

public class AdHolder implements MoPubNative.MoPubNativeNetworkListener {

	private static final String TAG = "AdHolder";

	public interface OnAdHolderStateChangeListener {
		void onAdHolderStateChange(AdHolder holder);
	}

	public enum State {
		None, Loading, Loaded, Error
	}

	public AdHolder(Context context, String adUnitId, ViewBinder binder) {
		mAdUnitId = adUnitId;
		mBinder = binder;
		mHandler = new Handler(Looper.getMainLooper());

		mState = State.None;

		final Context app = context.getApplicationContext();

		// Use application context for the ad
		moPubNative = new MoPubNative(app, adUnitId, this);

		// Use application context for the helper
		mAdapterHelper = new AdapterHelper(app, 0, 3);

		// This causes memory leak on orientation changes - all (!!!) activity instances ever get retained in
		// memory because of "bad" usage of WeakHashMap (references from values to keys)
		final MoPubStaticNativeAdRenderer renderer = new MoPubStaticNativeAdRenderer(mBinder);
		moPubNative.registerAdRenderer(renderer);

		// Note: even though we construct MoPubNative and AdapterHelper with Application Context -
		//
		// - we will leak *all* Activity instances and ad views ever created and bound (!!!).
		//
		// They will accumulate in:
		//
		// moPubNativeAd -> mMoPubAdRenderer -> mViewHolderMap
		//
		// which is a WeakHashMap where values (StaticNativeViewHolder's) have hard references to keys (View's) and
		// because of these references, the map never garbage collects anything.
		//
		// In addition, all views ever bound will accumulate in
		//
		// NativeAdViewHelper -> sNativeAdMap
		//
		// which is a static final and never goes way.
		//
		// It's also a WeakHashMap that never collects anything because its View key objects are same as those in
		// mViewHolderMap above and those are never collected.
		//
	}

	public void setListener(OnAdHolderStateChangeListener listener) {
		mListener = listener;
	}

	public void start() {
		if (mState == State.None || mState == State.Error) {
			mState = State.Loading;
			moPubNative.makeRequest();
		}
		postCallListener();
	}

	public String getAdUnitId() {
		return mAdUnitId;
	}

	public State getState() {
		return mState;
	}

	public View getView(Activity activity, View convertView, ViewGroup parentView) {
		if (mState != State.Loaded || moPubNativeAd == null) {
			throw new IllegalStateException("AdHolder state is not " + State.Loaded + " or ad is null");
		}

		convertView = mAdapterHelper.getAdView(convertView, parentView, moPubNativeAd, mBinder);

		return convertView;
	}

	public void onActivityDestroy() {
		if (moPubNative != null) {
			moPubNative.destroy();
			moPubNative = null;
		}

		if (moPubNativeAd != null) {
			moPubNativeAd.destroy();
			moPubNativeAd = null;
		}
	}

	@Override
	public void onNativeLoad(NativeAd nativeAd) {
		MyLog.i(TAG, "onNativeLoad: %s", nativeAd);

		moPubNativeAd = nativeAd;

		mState = State.Loaded;
		postCallListener();
	}

	@Override
	public void onNativeFail(NativeErrorCode nativeErrorCode) {
		MyLog.i(TAG, "onNativeFail: %s", nativeErrorCode);

		moPubNativeAd = null;

		mState = State.Error;
		postCallListener();
	}

	private void postCallListener() {
		mHandler.post(this::callListener);
	}

	private void callListener() {
		if (mListener != null) {
			mListener.onAdHolderStateChange(this);
		}
	}

	private final String mAdUnitId;
	private final ViewBinder mBinder;
	private final Handler mHandler;

	private State mState;

	private OnAdHolderStateChangeListener mListener;

	private MoPubNative moPubNative;
	private AdapterHelper mAdapterHelper;

	private NativeAd moPubNativeAd;
}
