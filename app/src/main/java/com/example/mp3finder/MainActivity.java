package com.example.mp3finder;

import android.app.Activity;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;

public class MainActivity extends Activity {
    EditText searchInput;
    LinearLayout results;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText("MP3 FINDER");
        title.setTextSize(28);
        layout.addView(title);

        searchInput = new EditText(this);
        searchInput.setHint("Название песни или исполнитель");
        layout.addView(searchInput);

        Button searchButton = new Button(this);
        searchButton.setText("ИСКАТЬ");
        layout.addView(searchButton);

        ScrollView scroll = new ScrollView(this);
        results = new LinearLayout(this);
        results.setOrientation(1);
        scroll.addView(results);
        layout.addView(scroll);

        setContentView(layout);
        searchButton.setOnClickListener(v -> search());
    }

    private String request(String address) throws Exception {
        HttpURLConnection c =
            (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);

        BufferedReader r = new BufferedReader(
            new InputStreamReader(c.getInputStream()));

        StringBuilder b = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) b.append(line);

        r.close();
        c.disconnect();
        return b.toString();
    }

    private void search() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) return;

        results.removeAllViews();
        addText("Ищем...");

        new Thread(() -> {
            try {
                String q = URLEncoder.encode(query, "UTF-8");
                String address =
                    "https://archive.org/advancedsearch.php?q="
                    + q + "+AND+mediatype%3Aaudio"
                    + "&fl[]=identifier&fl[]=title&rows=20&output=json";

                JSONObject json = new JSONObject(request(address));
                JSONArray docs = json.getJSONObject("response")
                                     .getJSONArray("docs");

                runOnUiThread(() -> {
                    results.removeAllViews();

                    if (docs.length() == 0) {
                        addText("Ничего не найдено");
                        return;
                    }

                    for (int i = 0; i < docs.length(); i++) {
                        JSONObject item = docs.optJSONObject(i);
                        String name = item.optString("title", "Без названия");
                        String id = item.optString("identifier", "");

                        TextView label = new TextView(this);
                        label.setText(name);
                        label.setTextSize(17);
                        label.setPadding(5, 15, 5, 5);
                        results.addView(label);

                        Button download = new Button(this);
                        download.setText("Проверить лицензию и скачать MP3");
                        results.addView(download);

                        download.setOnClickListener(v ->
                            checkAndDownload(id, download));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    results.removeAllViews();
                    addText("Ошибка поиска. Проверь интернет.");
                });
            }
        }).start();
    }

    private void checkAndDownload(String id, Button button) {
        button.setEnabled(false);
        button.setText("Проверяем...");

        new Thread(() -> {
            try {
                String address =
                    "https://archive.org/metadata/" +
                    URLEncoder.encode(id, "UTF-8");

                JSONObject data = new JSONObject(request(address));
                JSONObject metadata = data.optJSONObject("metadata");

                String license = metadata == null ? "" :
                    metadata.optString("licenseurl", "");

                boolean allowed =
                    license.contains("creativecommons.org/licenses/") ||
                    license.contains("creativecommons.org/publicdomain/");

                if (!allowed) {
                    runOnUiThread(() -> {
                        button.setText("Нет подтверждённой свободной лицензии");
                        button.setEnabled(true);
                    });
                    return;
                }

                JSONArray files = data.optJSONArray("files");
                String filename = null;

                if (files != null) {
                    for (int i = 0; i < files.length(); i++) {
                        JSONObject f = files.optJSONObject(i);
                        String n = f.optString("name", "");
                        if (n.toLowerCase().endsWith(".mp3")) {
                            filename = n;
                            break;
                        }
                    }
                }

                if (filename == null) {
                    runOnUiThread(() -> {
                        button.setText("MP3 не найден");
                        button.setEnabled(true);
                    });
                    return;
                }

                String fileUrl = "https://archive.org/download/"
                    + URLEncoder.encode(id, "UTF-8") + "/"
                    + Uri.encode(filename);

                String safeName = filename.substring(
                    filename.lastIndexOf('/') + 1);

                DownloadManager.Request req =
                    new DownloadManager.Request(Uri.parse(fileUrl));

                req.setTitle(safeName);
                req.setDescription("Загрузка MP3");
                req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, safeName);

                DownloadManager manager =
                    (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                manager.enqueue(req);

                runOnUiThread(() -> {
                    button.setText("Загрузка началась!");
                    button.setEnabled(true);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    button.setText("Ошибка. Попробуй ещё раз");
                    button.setEnabled(true);
                });
            }
        }).start();
    }

    private void addText(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16);
        t.setPadding(5, 15, 5, 15);
        results.addView(t);
    }
}
