package com.nc13ja.hollowsavetranslator;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Iterator;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {
    private static final int PICK_SAVE = 1;
    private static final int CREATE_SAVE = 2;
    private static final int CREATE_BACKUP = 3;
    private static final byte[] HEADER = new byte[] {0,1,0,0,0,(byte)255,(byte)255,(byte)255,(byte)255,1,0,0,0,0,0,0,0,6,1,0,0,0};
    private static final String KEY = "UKu52ePUBwetZ9wNX88o54dnfKRu0T1l";
    private byte[] originalBytes;
    private byte[] exportBytes;
    private JSONObject saveJson;
    private final ArrayList<Field> fields = new ArrayList<>();
    private LinearLayout fieldContainer;
    private TextView status;
    private static class Field {
        String key;
        EditText input;
        Field(String key, EditText input) { this.key = key; this.input = input; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Hollow Save Translator");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        content.addView(title);
        TextView intro = new TextView(this);
        intro.setText("أداة أولية لقراءة وتعديل بعض القيم الرقمية في ملفات حفظ Hollow Knight على الكمبيوتر. اختر ملف الحفظ الأصلي؛ سيُصدَّر التعديل إلى ملف جديد ولن يُستبدل الأصل.");
        intro.setTextSize(16);
        content.addView(intro);
        Button open = new Button(this);
        open.setText("اختيار ملف الحفظ");
        open.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, PICK_SAVE);
        });
        content.addView(open);
        status = new TextView(this);
        status.setText("لم يتم اختيار ملف بعد.");
        status.setPadding(0, 12, 0, 12);
        content.addView(status);
        fieldContainer = new LinearLayout(this);
        fieldContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(fieldContainer);
        Button save = new Button(this);
        save.setText("تصدير ملف الحفظ المعدّل");
        save.setOnClickListener(v -> exportEdited());
        content.addView(save);
        Button backup = new Button(this);
        backup.setText("تصدير نسخة مطابقة من الأصل");
        backup.setOnClickListener(v -> {
            if (originalBytes == null) { toast("اختر ملف الحفظ أولاً"); return; }
            exportBytes = originalBytes.clone();
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("application/octet-stream");
            i.putExtra(Intent.EXTRA_TITLE, "user1_backup.dat");
            startActivityForResult(i, CREATE_BACKUP);
        });
        content.addView(backup);
        scroll.addView(content);
        root.addView(scroll);
        setContentView(root);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == PICK_SAVE) loadSave(uri);
            else if (requestCode == CREATE_SAVE || requestCode == CREATE_BACKUP) writeExport(uri);
        } catch (Exception e) {
            status.setText("حدث خطأ: " + e.getMessage());
            toast("تعذر إكمال العملية: " + e.getMessage());
        }
    }

    private byte[] readAll(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("تعذر فتح الملف");
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private void loadSave(Uri uri) throws Exception {
        byte[] bytes = readAll(uri);
        if (bytes.length <= HEADER.length + 2) throw new IllegalArgumentException("الملف قصير جداً أو غير مدعوم");
        for (int i = 0; i < HEADER.length; i++) {
            if (bytes[i] != HEADER[i]) throw new IllegalArgumentException("ترويسة الملف لا تطابق صيغة PC المعروفة. لم يتم تعديل الملف.");
        }
        if (bytes[bytes.length - 1] != 11) throw new IllegalArgumentException("نهاية الملف غير متوقعة؛ قد تكون الصيغة أو نسخة اللعبة مختلفة.");
        byte[] b64 = new byte[bytes.length - HEADER.length - 1];
        System.arraycopy(bytes, HEADER.length, b64, 0, b64.length);
        byte[] cipherText = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"));
        byte[] plain = cipher.doFinal(cipherText);
        String jsonText = new String(plain, StandardCharsets.UTF_8);
        JSONObject parsed = new JSONObject(jsonText);
        originalBytes = bytes;
        saveJson = parsed;
        fields.clear();
        fieldContainer.removeAllViews();
        scanObject(parsed, "");
        status.setText("تم فتح الملف. الحقول الرقمية المرشحة: " + fields.size() + ". راجع القيم قبل التصدير.");
    }

    private boolean likelyEditable(String key) {
        String k = key.toLowerCase();
        String[] words = {"geo", "essence", "paleore ore", "paleore", "simplekey", "elegantkey", "kingsbrand",
            "rancidegg", "rancid", "grub", "maskshard", "vessel", "charmnotch", "notch", "ore",
            "dreamnail", "dreamgate", "lantern", "map", "key", "health", "healthpiece", "soul"};
        for (String word : words) if (k.contains(word.replace(" ", ""))) return true;
        return false;
    }

    private void scanObject(JSONObject obj, String prefix) throws JSONException {
        Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            String key = it.next();
            Object value = obj.get(key);
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (value instanceof JSONObject) {
                scanObject((JSONObject)value, path);
            } else if (value instanceof Number && likelyEditable(key)) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                TextView label = new TextView(this);
                label.setText(path);
                row.addView(label);
                EditText edit = new EditText(this);
                edit.setSingleLine(true);
                edit.setInputType( android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                edit.setText(String.valueOf(value));
                row.addView(edit);
                fieldContainer.addView(row);
                fields.add(new Field(path, edit));
            }
        }
    }

    private void setPath(JSONObject root, String path, Object value) throws JSONException {
        String[] parts = path.split("\\.");
        JSONObject current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object child = current.opt(parts[i]);
            if (!(child instanceof JSONObject)) throw new JSONException("مسار غير مدعوم: " + path);
            current = (JSONObject)child;
        }
        String leaf = parts[parts.length - 1];
        Object old = current.opt(leaf);
        if (old instanceof Integer || old instanceof Long) {
            long n = Long.parseLong(value.toString());
            if (n < Integer.MIN_VALUE || n > Long.MAX_VALUE) throw new NumberFormatException("قيمة خارج النطاق");
            current.put(leaf, n);
        } else {
            current.put(leaf, Double.parseDouble(value.toString()));
        }
    }

    private void exportEdited() {
        if (saveJson == null) { toast("اختر ملف الحفظ أولاً"); return; }
        try {
            JSONObject edited = new JSONObject(saveJson.toString());
            for (Field f : fields) {
                String value = f.input.getText().toString().trim();
                if (value.isEmpty()) throw new IllegalArgumentException("أدخل قيمة لكل حقل ظاهر");
                Object old = getPathValue(edited, f.key);
                if (old instanceof Integer || old instanceof Long) setPath(edited, f.key, Long.parseLong(value));
                else setPath(edited, f.key, Double.parseDouble(value));
            }
            String json = edited.toString();
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"));
            byte[] encrypted = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
            byte[] encoded = android.util.Base64.encode(encrypted, android.util.Base64.NO_WRAP);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(HEADER); out.write(encoded); out.write(11);
            exportBytes = out.toByteArray();
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("application/octet-stream");
            i.putExtra(Intent.EXTRA_TITLE, "user1_modified.dat");
            startActivityForResult(i, CREATE_SAVE);
        } catch (Exception e) {
            toast("تعذر تجهيز الملف: " + e.getMessage());
        }
    }

    private Object getPathValue(JSONObject root, String path) throws JSONException {
        String[] parts = path.split("\\.");
        Object cur = root;
        for (String part : parts) {
            if (!(cur instanceof JSONObject)) throw new JSONException("مسار غير مدعوم");
            cur = ((JSONObject)cur).get(part);
        }
        return cur;
    }

    private void writeExport(Uri uri) throws Exception {
        if (exportBytes == null) throw new IllegalStateException("لا توجد بيانات جاهزة للتصدير");
        try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
            if (out == null) throw new IllegalStateException("تعذر إنشاء الملف");
            out.write(exportBytes); out.flush();
        }
        status.setText("اكتمل التصدير. احتفظ بالأصل ولا تستبدله قبل اختبار الملف داخل اللعبة.");
        toast("تم تصدير الملف");
        exportBytes = null;
    }
}
