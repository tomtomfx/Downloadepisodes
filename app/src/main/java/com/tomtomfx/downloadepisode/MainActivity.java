package com.tomtomfx.downloadepisode;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, Callback {

    LinearLayout linearLayout;
    private Button settingsButton;
    private Button requestButton;
    private TextView resultsTextView;
    private ListView resultsListView;
    private Snackbar snackbar;
    private long downloadID;

    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Retrieve main layout
        linearLayout = findViewById(R.id.linearLayout);
        // Retrieve button and add listener
        settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(this);
        // Retrieve button and add listener
        requestButton = findViewById(R.id.requestButton);
        requestButton.setOnClickListener(this);
        // Retrieve textView
        resultsTextView = findViewById(R.id.resultsTextView);
        // retrieve listView
        resultsListView = findViewById(R.id.resultsListView);
        // Create snackbar on linearLayout
        snackbar = Snackbar.make(linearLayout, "Requête en cours d'exécution",
                Snackbar.LENGTH_INDEFINITE);

    }

    @Override
    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.requestButton:
                if (!isConnected()) {
                    Snackbar.make(view, "Aucune connexion à internet.", Snackbar.LENGTH_LONG).show();
                    return;
                }
                snackbar.show();

                JSONObject postdata = new JSONObject();
                try {
                    postdata.put("tablet", "Pixel C");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                RequestBody req = RequestBody.create(MediaType.parse("application/json"), postdata.toString());

                Request request = new Request.Builder()
                        .url("http://192.168.1.5/api/episodes/getEpisodesToCopy.php")
                        .method("POST", req)
                        .header("Content-Type", "application/json")
                        .build();
                client.newCall(request).enqueue(this);
                break;
            case R.id.settingsButton:
                Intent preferences = new Intent(MainActivity.this, AppPreferences.class);
                startActivity(preferences);
                break;
        }
    }

    private boolean isConnected() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    @Override
    public void onFailure(Call call, IOException e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                resultsTextView.setText("Erreur");
                snackbar.dismiss();
            }
        });
    }

    @Override
    public void onResponse(Call call, Response response) throws IOException {
        // Get episodes to download in a JSON format
        JSONObject results = null;

        // Create a list of all the elements to download
        List<HashMap<String, String>> downloads = new ArrayList<HashMap<String, String>>();
        HashMap<String, String> element;

        final String body = response.body().string();
        try{
            results = new JSONObject(body);
        }
        catch (JSONException e){
            e.printStackTrace();
        }

        // Print results on the app
        final String res = getStringFormat(results);
        if (!response.isSuccessful()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resultsTextView.setText(res);
                    snackbar.dismiss();
                }
            });
        }
        else {
            // Download all requested files
            Iterator<String> keys = results.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    if (results.get(key) instanceof JSONObject) {
                        element = new HashMap<String, String>();
                        JSONObject episode = (JSONObject) results.get(key);
                        element.put("id", key);
                        element.put("video", episode.getString("Video"));
                        element.put("subtitles", episode.getString("Subtitles"));
                        downloads.add(element);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            ListAdapter adapter = new SimpleAdapter(this,
                    downloads,
                    R.layout.list_3_items,
                    new String[] {"id", "video", "subtitles"},
                    new int[] {R.id.line1, R.id.line2, R.id.line3 });
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resultsListView.setAdapter(adapter);
                    snackbar.dismiss();
                }
            });
            //downloadFiles(key, episode.getString("Video"), episode.getString("Subtitles"));
        }
    }

    private String getStringFormat(JSONObject json){
        try{
            return json.toString(4);
        }
        catch (JSONException e){
            return "0";
        }
    }

    private void downloadFiles (String episodeID, String video, String subs){
        final String videoURL = video;
        final String subsURL = subs;

        DownloadManager downloadManager= (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
/*
        DownloadManager.Request videoRequest=new DownloadManager.Request(Uri.parse(videoURL))
                .setTitle(episodeID)
                .setDescription("Downloading")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.parse("/storage/emulated/0/Movies/"))
                .setRequiresCharging(false)
                .setAllowedOverMetered(false)
                .setAllowedOverRoaming(false);
        downloadID = downloadManager.enqueue(videoRequest);
*/
        DownloadManager.Request subRequest=new DownloadManager.Request(Uri.parse(subsURL))
                .setTitle(episodeID)
                .setDescription("Downloading")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.parse("/storage/emulated/0/Movies/"))
                .setRequiresCharging(false)
                .setAllowedOverMetered(false)
                .setAllowedOverRoaming(false);
        //downloadID = downloadManager.enqueue(subRequest);
    }

}
