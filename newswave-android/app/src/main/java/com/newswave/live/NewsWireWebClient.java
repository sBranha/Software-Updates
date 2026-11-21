package com.newswave.live;

import android.app.Activity;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import androidx.webkit.WebViewAssetLoader;

/** HTTPS asset origin and explicit recovery if Android terminates a renderer. */
class NewsWireWebClient extends WebViewClient {
    private WebViewAssetLoader assets;
    @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {
        if(!"https".equalsIgnoreCase(request.getUrl().getScheme())
            ||!"appassets.androidplatform.net".equalsIgnoreCase(request.getUrl().getHost()))return null;
        if(assets==null)assets=new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(view.getContext())).build();
        return assets.shouldInterceptRequest(request.getUrl());
    }
    @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail) {
        android.content.Context context=view.getContext();
        if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
        view.destroy();
        if(context instanceof Activity){
            Activity activity=(Activity)context;
            TextView retry=new TextView(activity);
            retry.setText("This page stopped responding.\n\nTap here to reload NewsWire.");
            retry.setTextColor(android.graphics.Color.WHITE);
            retry.setBackgroundColor(0xff071422);
            retry.setTextSize(20);
            retry.setGravity(android.view.Gravity.CENTER);
            retry.setPadding(32,32,32,32);
            retry.setOnClickListener(v->activity.recreate());
            activity.setContentView(retry);
        }
        return true;
    }
}
