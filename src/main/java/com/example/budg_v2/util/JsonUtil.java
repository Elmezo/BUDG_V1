package com.example.budg_v2.util;

import com.google.gson.*;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class JsonUtil {

    // Gson instance configured with custom date format and Java 8 time adapters
    // serializeNulls() ensures that NULL values are included in JSON (not omitted)
    private static final Gson gson = new GsonBuilder()
            .serializeNulls() // Include NULL values in JSON output
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .registerTypeAdapter(LocalTime.class, new LocalTimeAdapter())
            .create();

    // ============================
    // Parse JSON from request
    // ============================
    public static JsonObject parseJsonFromRequest(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            sb.append(line.trim());
        }

        if (sb.length() == 0) {
            throw new IllegalArgumentException("Request body is empty");
        }

        try {
            return JsonParser.parseString(sb.toString()).getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid JSON format", e);
        }
    }

    // ============================
    // Extract values from JSON
    // ============================
    public static String getJsonString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : null;
    }

    public static Integer getJsonInt(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() ? element.getAsInt() : null;
    }

    public static Boolean getJsonBoolean(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() ? element.getAsBoolean() : null;
    }

    public static Double getJsonDouble(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() ? element.getAsDouble() : null;
    }

    // ============================
    // Send JSON responses
    // ============================
    public static void sendJsonResponse(PrintWriter out, Object data) {
        out.println(gson.toJson(data));
        out.flush();
    }

    public static void sendErrorResponse(PrintWriter out, String message, int statusCode) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        error.addProperty("status", statusCode);
        out.println(error.toString());
        out.flush();
    }

    public static void sendSuccessResponse(PrintWriter out, String message, JsonObject data) {
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("message", message);

        if (data != null && !data.entrySet().isEmpty()) {
            response.add("data", data);
        }

        out.println(response.toString());
        out.flush();
    }

    // ============================
    // Convert objects to/from JSON
    // ============================
    public static String toJson(Object obj) {
        return gson.toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> classOfT) {
        try {
            return gson.fromJson(json, classOfT);
        } catch (JsonSyntaxException e) {
            System.err.println("Failed to parse JSON: " + e.getMessage());
            return null;
        }
    }

    // ============================
    // Helpers: parse arrays of objects into List<Map<String,Object>>
    // ============================
    public static java.util.List<java.util.Map<String, Object>> getAsListOfMaps(JsonObject json, String key) {
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        JsonElement el = json.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonArray()) return list;
        JsonArray arr = el.getAsJsonArray();
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) continue;
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            JsonObject o = e.getAsJsonObject();
            for (java.util.Map.Entry<String, JsonElement> entry : o.entrySet()) {
                JsonElement v = entry.getValue();
                if (v == null || v.isJsonNull()) {
                    map.put(entry.getKey(), null);
                } else if (v.isJsonPrimitive()) {
                    JsonPrimitive p = v.getAsJsonPrimitive();
                    if (p.isBoolean()) map.put(entry.getKey(), p.getAsBoolean());
                    else if (p.isNumber()) map.put(entry.getKey(), p.getAsNumber());
                    else map.put(entry.getKey(), p.getAsString());
                } else if (v.isJsonArray() || v.isJsonObject()) {
                    map.put(entry.getKey(), v.toString());
                } else {
                    map.put(entry.getKey(), v.toString());
                }
            }
            list.add(map);
        }
        return list;
    }

    // ============================
    // Gson TypeAdapters for Java 8 Time types
    // ============================

    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(formatter));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String dateTimeString = in.nextString();
            return LocalDateTime.parse(dateTimeString, formatter);
        }
    }

    private static class LocalDateAdapter extends TypeAdapter<LocalDate> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        @Override
        public void write(JsonWriter out, LocalDate value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(formatter));
            }
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String dateString = in.nextString();
            return LocalDate.parse(dateString, formatter);
        }
    }

    private static class LocalTimeAdapter extends TypeAdapter<LocalTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        @Override
        public void write(JsonWriter out, LocalTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(formatter));
            }
        }

        @Override
        public LocalTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String timeString = in.nextString();
            return LocalTime.parse(timeString, formatter);
        }
    }
}
