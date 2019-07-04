package com.tomtomfx.downloadepisode;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

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
    private TextView resultsTextView;
    private ListView resultsListView;
    private String tabletID;
    private String server;
    SharedPreferences preferences;

    DownloadManager downloadManager;
    HashMap<Long, String> downloadIDs = new HashMap<>();
    private IntentFilter downloadCompleteIntentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
    private BroadcastReceiver downloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L);
            if (downloadIDs.containsKey(id))
            {
                String filename = downloadIDs.get(id);
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(id);
                Cursor cursor = downloadManager.query(query);
                cursor.moveToFirst();

                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                if (DownloadManager.STATUS_SUCCESSFUL != cursor.getInt(statusIndex)){
                    int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                    String reason = cursor.getString(reasonIndex);
                    Toast.makeText(MainActivity.this, "Download failed: "+reason, Toast.LENGTH_LONG).show();
                }
                else{
                    Toast.makeText(MainActivity.this, "Download completed: "+filename, Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Retrieve main layout
        linearLayout = findViewById(R.id.linearLayout);
        // Retrieve button and add listener
        Button settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(this);
        // Retrieve button and add listener
        Button requestButton = findViewById(R.id.requestButton);
        requestButton.setOnClickListener(this);
        // Retrieve textView
        resultsTextView = findViewById(R.id.resultsTextView);
        // retrieve listView
        resultsListView = findViewById(R.id.resultsListView);

        // Register broadcast receiver
        MainActivity.this.registerReceiver(downloadComplete, downloadCompleteIntentFilter);

    }

    @Override
    public void onResume(){
        super.onResume();
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        tabletID = preferences.getString("tablet", "");
        server = preferences.getString("server", "");
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.requestButton:
                if (!isConnected()) {
                    Toast.makeText(MainActivity.this, "Aucune connexion à internet.", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(MainActivity.this, "Requête en cours d'exécution.", Toast.LENGTH_LONG).show();

                JSONObject postdata = new JSONObject();
                try {
                    postdata.put("tablet", tabletID);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                RequestBody req = RequestBody.create(MediaType.parse("application/json"), postdata.toString());

                Request request = new Request.Builder()
                        .url("http://"+server+"/api/episodes/getEpisodesToCopy.php")
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
                resultsTextView.setText("Error");
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
                    resultsListView.setAdapter(null);
                }
            });
        }
        else {
            // Retrieve all requested episodes
            Iterator<String> keys = results.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    if (results.get(key) instanceof JSONObject) {
                        element = new HashMap<>();
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
                    resultsTextView.setText("");
                    resultsListView.setAdapter(adapter);
                }
            });

            Iterator<HashMap<String, String>> downloadsIterator = downloads.iterator();
            while(downloadsIterator.hasNext()) {
                HashMap<String, String> download = downloadsIterator.next();
                downloadFiles(download.get("id"), download.get("video"), download.get("subtitles"));
            }

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
        String filename;
        int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        }
        else {

            long downloadID;
            downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);


            filename = video.substring( video.lastIndexOf('/')+1, video.length() );
            DownloadManager.Request videoRequest=new DownloadManager.Request(Uri.parse(video))
                    .setTitle(episodeID)
                    .setDescription("Downloading")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalPublicDir("/Movies", filename)
                    .setRequiresCharging(false)
                    .setAllowedOverMetered(false)
                    .setAllowedOverRoaming(false);
            downloadID = downloadManager.enqueue(videoRequest);

            filename = subs.substring(subs.lastIndexOf('/') + 1, subs.length());
            DownloadManager.Request subRequest = new DownloadManager.Request(Uri.parse(subs))
                    .setTitle(episodeID)
                    .setDescription("Download " + filename)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalPublicDir("/Movies", filename)
                    .setRequiresCharging(false)
                    .setAllowedOverMetered(false)
                    .setAllowedOverRoaming(false);
            downloadID = downloadManager.enqueue(subRequest);
            downloadIDs.put(downloadID, filename);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(downloadComplete);
    }

}
