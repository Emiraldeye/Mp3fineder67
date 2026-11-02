package com.example.mp3finder;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.net.URLEncoder;

public class MainActivity extends Activity {

    EditText searchInput;
    LinearLayout results;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("MP3 Finder");
        title.setTextSize(28);
        layout.addView(title);

        searchInput = new EditText(this);
        searchInput.setHint("Название песни или исполнитель");
        layout.addView(searchInput);

        Button searchButton = new Button(this);
        searchButton.setText("ИСКАТЬ");
        layout.addView(searchButton);

        results = new LinearLayout(this);
        results.setOrientation(1);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(results);
        layout.addView(scroll);

        setContentView(layout);

        searchButton.setOnClickListener(v -> search());
    }

    private void search() {
        String query = searchInput.getText().toString().trim();

        if (query.isEmpty()) {
            Toast.makeText(this, "Введи название музыки",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        results.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("Поиск...");
        results.addView(loading);

        new Thread(() -> {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String address =
                    "https://archive.org/advancedsearch.php?q="
                    + encoded
                    + "&fl[]=identifier&fl[]=title&rows=20&output=json";

                URL url = new URL(address);
                HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));

                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();
                connection.disconnect();

                JSONObject json = new JSONObject(response.toString());
                JSONArray docs = json.getJSONObject("response")
                                     .getJSONArray("docs");

                runOnUiThread(() -> {
                    results.removeAllViews();

                    if (docs.length() == 0) {
                        addResult("Ничего не найдено");
                        return;
                    }

                    for (int i = 0; i < docs.length(); i++) {
                        JSONObject item = docs.optJSONObject(i);
                        String name = item.optString("title", "Без названия");
                        String id = item.optString("identifier", "");

                        addResult(name + "\nhttps://archive.org/details/" + id);
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    results.removeAllViews();
                    addResult("Ошибка поиска. Проверь интернет.");
                });
            }
        }).start();
    }

    private void addResult(String text) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setTextSize(16);
        item.setPadding(8, 16, 8, 16);
        results.addView(item);
    }
}
