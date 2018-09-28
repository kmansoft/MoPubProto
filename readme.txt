Instructions to reproduce a memory leak in MoPub SDK that happens when caching MoPubNative objects across Activity
instances (say, orientation changes).


1 - Run the app

2 - Wait for the ad to load

3 - Rotate the activity several times (say, 5 or more), waiting the ad to show (bind) each time

4 - Use Android Studio memory profiler to force GC (several times to be sure) then collect a heap dump

5 - Filter on MainActivity - you will see that all instances ever created (5 or more) are leaked

6 - Set a breakpoint in NativeAdViewHelper # getAdView, rotate the phone - inspect "static members" -> sNativeAdMap

    Even though we just forced garbage collection - sNativeAdMap will contain all ad views ever bound


Then please see AdHolder for a large comment with my analysis of what's happening.


Fixes are necessary in two places:

***** 1 - MoPubStaticNativeAdRenderer # mViewHolderMap *****

WeakHashMap doesn't work here because it's only "weak" with respect to its keys, but not to its values. When values
have hard references to their keys, nothing can be collected.

This chain of references is shown in the included screenshot, basically StaticNativeViewHolder has a "mainView"
which is exactly our "key" in this map but also each of the views in the view holder indirectly references "mainView"
through its chain of "parent view"'s.

To add insult to injury, a WeakHashMap only releases its values in this internal method:

https://android.googlesource.com/platform/libcore/+/refs/heads/master/ojluni/src/main/java/java/util/WeakHashMap.java#314

which is used when there is a call to the map's size() or iteration and such.

A WeakHashMap "left on its own" (without calls to size or iteration) will not release any values even if their keys
have been GC'd - it simply will not know what happened.

In summary:

WeakHashMap is not a magic bullet. It's easy to shoot yourself in the foot.

* Here is one possible fix:

WeakHashMap<key, WeakReference<value> >

This would be able to collect a key / value pair even if key is referenced from value, and without requiring calls to
size() iteration.

* Another possible fix:

Use a List<WeakReference<StaticNativeViewHolder>>

Look for any already existing view holder like this:

    private StaticNativeViewHolder findHolderForView(View view) {
        final Iterator<WeakReference<StaticNativeViewHolder>> iterator = mViewHolderList.iterator();
        while (iterator.hasNext()) {
            // Copy referenced object into a stable (hard) reference first
            final StaticNativeViewHolder holder = iterator.next().get();
            if (holder == null) {
                // The view holder has been GC'd
                iterator.remove();
            } else if (holder.mainView == view) {
                // The one we need
                return holder;
            }
        }
        return null;
    }

Should not be a performance problem: it's unlikely that there can be thousands or even hundreds of bound views per
NativeAd, especially if they're properly garbage collected.

* But perhaps best solution is to use setViewTag / getViewTag with a resources-generated ID like this:

		StaticNativeViewHolder staticNativeViewHolder = (StaticNativeViewHolder)
		    view.getTag(R.id.mopub_tag_MoPubStaticNativeAdRenderer_StaticNativeViewHolder);
		if (staticNativeViewHolder == null) {
			staticNativeViewHolder = StaticNativeViewHolder.fromViewBinder(view, this.mViewBinder);
			view.setTag(R.id.mopub_tag_MoPubStaticNativeAdRenderer_StaticNativeViewHolder, staticNativeViewHolder);
		}

This way there is no "external" (to views / holders) data structure at all, and nothing is going to prevent views
(and their contexts etc.) from being collected.

Note: using getTag() / setTag() on Android 2.* could cause memory leaks, same issue with WeakHashMap.

MoPub SDK's minSdk is 16, getTag / setTag have been fixed long ago, and then getTag(id) / setTag(id) are completely
separate methods that never had any issues with memory leaks in the first place.

***** 2 - NativeAdViewHelper # sNativeAdMap *****

This is also a WeakHashMap and it has same issues.

First its content is not collected because its keys (views) are held by mViewHolderMap issue (above).

Second (some of) its values again can have references to its keys (although it's not as direct as in the
mViewHolderMap case above), it's like this NativeAd -> mMoPubAdRenderer -> mViewHolderMap where NativeAd objects are
this map's values and views are keys.

Third, same thing as above with WeakMap only releasing its values from size() or iteration.

Using View tags again is a good fix.

class NativeAdViewHelper

    // Removed
    // private static final WeakHashMap<View, NativeAd> sNativeAdMap

	private static void clearNativeAd(@NonNull View view) {
		NativeAd nativeAd = (NativeAd) view.getTag(R.id.mopub_tag_NativeAdViewHelper_NativeAd);
		if (nativeAd != null) {
			view.setTag(R.id.mopub_tag_NativeAdViewHelper_NativeAd, null);
			nativeAd.clear(view);
		}
	}

	private static void prepareNativeAd(@NonNull View view, @NonNull NativeAd nativeAd) {
		view.setTag(R.id.mopub_tag_NativeAdViewHelper_NativeAd, nativeAd);
		nativeAd.prepare(view);
	}

In resources, to generate the unique id's:

    <resources>
        	<item name="mopub_tag_MoPubStaticNativeAdRenderer_StaticNativeViewHolder" type="id"/>
        	<item name="mopub_tag_NativeAdViewHelper_NativeAd" type="id"/>
    </resources>

View.setTag / getTag which take a "tag id" are available since Android 1.5 :)

PS - it may be be a good ideas to review the of WeakHashMap elsewhere in the SDK (ImpressionTracker,
VisibilityTracker, ...), although based on my testing, I didn't find any memory leaks in those.

-- Kostya Vasilyev

