package androidx.media;

/** Test-only capture for the AndroidX result's package-private delivery hook. */
public final class MediaResultCapture<T> extends MediaBrowserServiceCompat.Result<T> {
    public T value;
    public MediaResultCapture(){super("test");}
    @Override void onResultSent(T value){this.value=value;}
}
