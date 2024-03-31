package com.example.androidgpt;

import androidx.appcompat.app.AppCompatActivity;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private EditText editTextPrompt;
    private Button buttonSend;
    private TextView textViewResponseContent;
    private DatabaseHelper databaseHelper;
    private Button buttonSave;
    private TextView textViewSaveStatus;
    private TextView textViewAuditLogs;

    private static final String OPENAI_API_KEY = "dummy";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewSaveStatus = findViewById(R.id.textViewSaveStatus);
        editTextPrompt = findViewById(R.id.editTextPrompt);
        buttonSend = findViewById(R.id.buttonSend);
        textViewResponseContent = findViewById(R.id.textViewResponseContent);
        textViewAuditLogs = findViewById(R.id.textViewAuditLogs);
        databaseHelper = new DatabaseHelper(this);

        buttonSend.setOnClickListener(view -> new ChatGPTTask().execute(editTextPrompt.getText().toString()));

        buttonSave = findViewById(R.id.buttonSave);
        buttonSave.setOnClickListener(view -> saveAuditLog(editTextPrompt.getText().toString(), textViewResponseContent.getText().toString()));

        loadAndDisplayAuditLogs();
    }

    private void saveAuditLog(String prompt, String response) {
        if (!prompt.isEmpty() && !response.isEmpty()) {
            databaseHelper.saveAudit(prompt, response);
            textViewSaveStatus.setText("Saved!");
            loadAndDisplayAuditLogs();
        } else {
            textViewSaveStatus.setText("Nothing to save");
        }
    }

    private void loadAndDisplayAuditLogs() {
        Cursor cursor = databaseHelper.getAllAuditLogs();
        StringBuilder auditLogs = new StringBuilder();

        if (cursor != null && cursor.moveToFirst()) {
            do {
                int promptIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_PROMPT);
                int responseIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_RESPONSE);

                if (promptIndex >= 0 && responseIndex >= 0) {
                    String prompt = cursor.getString(promptIndex);
                    String response = cursor.getString(responseIndex);
                    auditLogs.append("Prompt: ").append(prompt).append("\n");
                    auditLogs.append("Response: ").append(response).append("\n\n");
                }
            } while (cursor.moveToNext());
        }

        if (cursor != null) cursor.close();

        textViewAuditLogs.setText(auditLogs.toString());
    }

    private class ChatGPTTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... prompts) {
            OkHttpClient client = new OkHttpClient();
            String jsonPayload = "{\"model\": \"gpt-3.5-turbo\",\"messages\": [{\"role\": \"system\", \"content\": \"You are a helpful assistant.\"},{\"role\": \"user\", \"content\": \"" + prompts[0].replace("\"", "\\\"") + "\"}]}";
            RequestBody body = RequestBody.create(jsonPayload, JSON);
            Request request = new Request.Builder().url("https://api.openai.com/v1/chat/completions").addHeader("Authorization", "Bearer " + OPENAI_API_KEY).post(body).build();

            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JSONObject jsonresponse = new JSONObject(responseBody);
                    JSONArray choicesArray = jsonresponse.getJSONArray("choices");
                    if (choicesArray.length() > 0) {
                        JSONObject choice = choicesArray.getJSONObject(0);
                        JSONObject message = choice.getJSONObject("message");
                        return message.getString("content");
                    } else {
                        return "Received empty choices array.";
                    }
                } else {
                    return "Error: " + response.code() + " " + response.message();
                }
            } catch (Exception e) {
                return "Failed to connect: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            textViewResponseContent.setText(result);
            String prompt = editTextPrompt.getText().toString();
            databaseHelper.saveAudit(prompt, result);
            loadAndDisplayAuditLogs();
        }
    }
}
