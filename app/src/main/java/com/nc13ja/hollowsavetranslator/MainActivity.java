package com.nc13ja.hollowsavetranslator;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
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
    private EditText search;
    private TextView summary;
    private int objectCount;
    private int arrayCount;
    private int nullCount;
    private String sourceName = "user1.dat";

    private static class Field {
        String path;
        String type;
        String originalValue;
        EditText input;
        TextView label;
        LinearLayout row;
        boolean editable;
        Field(String path, String type, String originalValue, EditText input,
              TextView label, LinearLayout row, boolean editable) {
            this.path = path; this.type = type; this.originalValue = originalValue;
            this.input = input; this.label = label; this.row = row; this.editable = editable;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setPadding(0, dp(5), 0, dp(5));
        return t;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(10));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        GradientDrawable backdrop = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF071523, 0xFF102B3B, 0xFF07111D});
        root.setBackground(backdrop);

        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF173A4C, 0xFF102334});
        heroBg.setCornerRadius(dp(20));
        hero.setBackground(heroBg);
        android.widget.ImageView emblem = new android.widget.ImageView(this);
        emblem.setImageResource(com.nc13ja.hollowsavetranslator.R.drawable.ic_knight);
        emblem.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        hero.addView(emblem, new LinearLayout.LayoutParams(dp(68), dp(78)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        TextView title = text("HOLLOW SAVE", 22);
        title.setTextColor(0xFFF0F5F7);
        title.setTypeface(null, Typeface.BOLD);
        heading.addView(title);
        TextView subtitle = text("TRANSLATOR  •  SAVE EDITOR", 11);
        subtitle.setTextColor(0xFF9CC7D4);
        heading.addView(subtitle);
        TextView intro = text("محرّر محلي لعرض بيانات الحفظ والبحث فيها وتصدير نسخة معدّلة.", 13);
        intro.setTextColor(0xFFD5E4EA);
        heading.addView(intro);
        hero.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(hero);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, dp(4));
        Button open = makeButton("＋  فتح ملف", true);
        open.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, PICK_SAVE);
        });
        actions.addView(open, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button backup = makeButton("نسخ احتياطي", false);
        backup.setOnClickListener(v -> exportOriginal());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(48), 1);
        bp.setMargins(dp(8), 0, 0, 0);
        actions.addView(backup, bp);
        root.addView(actions);

        status = text("اختر ملف حفظ PC متوافقًا للبدء.", 12);
        status.setTextColor(0xFFB8D3DE);
        root.addView(status);
        summary = text("لم يتم تحميل بيانات بعد.", 12);
        summary.setTextColor(0xFF78D9CF);
        summary.setTypeface(null, Typeface.BOLD);
        root.addView(summary);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(0xFFF3F7F9);
        search.setHintTextColor(0xFF90AAB6);
        search.setHint("⌕  ابحث في المسارات والقيم: geo، charm، nail، grub…");
        search.setTextSize(14);
        search.setPadding(dp(14), dp(4), dp(14), dp(4));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(0xFF142A39);
        searchBg.setCornerRadius(dp(14));
        searchBg.setStroke(dp(1), 0xFF315363);
        search.setBackground(searchBg);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(50)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterFields(s.toString()); }
            @Override public void afterTextChanged(Editable e) {}
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        fieldContainer = new LinearLayout(this);
        fieldContainer.setOrientation(LinearLayout.VERTICAL);
        fieldContainer.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(fieldContainer);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, 0, 1);
        sp.topMargin = dp(4);
        root.addView(scroll, sp);

        Button save = makeButton("تصدير التعديلات إلى ملف جديد  ↓", true);
        save.setOnClickListener(v -> exportEdited());
        root.addView(save, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView warning = text("احتفظ بالأصل دائمًا. بعض الحقول مترابطة؛ اختبر الملف الجديد قبل اعتماده.", 11);
        warning.setTextColor(0xFFFFD18A);
        root.addView(warning);
        setContentView(root);
    }

    private Button makeButton(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(primary ? 0xFF08212B : 0xFFE3F2F5);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                primary ? new int[]{0xFF77E1D0, 0xFFB6E9D6} : new int[]{0xFF234454, 0xFF1B3546});
        bg.setCornerRadius(dp(15));
        if (!primary) bg.setStroke(dp(1), 0xFF416477);
        b.setBackground(bg);
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == PICK_SAVE) {
                String name = data.getData().getLastPathSegment();
                if (name != null && !name.trim().isEmpty()) sourceName = name;
                loadSave(uri);
            } else if (requestCode == CREATE_SAVE || requestCode == CREATE_BACKUP) {
                writeExport(uri);
            }
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
        if (bytes.length <= HEADER.length + 2) throw new IllegalArgumentException("الملف قصير جدًا أو غير مدعوم");
        for (int i = 0; i < HEADER.length; i++) {
            if (bytes[i] != HEADER[i]) throw new IllegalArgumentException("الترويسة لا تطابق صيغة PC المعروفة. لم يتم تعديل الملف.");
        }
        if (bytes[bytes.length - 1] != 11) throw new IllegalArgumentException("نهاية الملف غير متوقعة؛ قد تكون الصيغة أو نسخة اللعبة مختلفة.");
        byte[] b64 = new byte[bytes.length - HEADER.length - 1];
        System.arraycopy(bytes, HEADER.length, b64, 0, b64.length);
        byte[] cipherText = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"));
        byte[] plain = cipher.doFinal(cipherText);
        JSONObject parsed = new JSONObject(new String(plain, StandardCharsets.UTF_8));

        originalBytes = bytes;
        saveJson = parsed;
        fields.clear();
        fieldContainer.removeAllViews();
        objectCount = 0; arrayCount = 0; nullCount = 0;
        scanValue(parsed, "");
        filterFields(search.getText().toString());
        summary.setText("كل العقد: " + fields.size() + "  •  كائنات: " + objectCount + "  •  مصفوفات: " + arrayCount + "  •  قيم null: " + nullCount);
        status.setText("تم فتح " + sourceName + " وفك تشفيره محليًا. ابحث في كل المسارات، ثم صدّر نسخة جديدة.");
    }

    private String childPath(String parent, String token) {
        String escaped = token.replace("~", "~0").replace("/", "~1");
        return parent + "/" + escaped;
    }

    private void scanValue(Object value, String path) throws JSONException {
        // Add containers as visible entries too, including empty objects and arrays.
        if (value instanceof JSONObject) {
            objectCount++;
            addField(path, value);
            JSONObject obj = (JSONObject)value;
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                scanValue(obj.get(key), childPath(path, key));
            }
        } else if (value instanceof JSONArray) {
            arrayCount++;
            addField(path, value);
            JSONArray arr = (JSONArray)value;
            for (int i = 0; i < arr.length(); i++) scanValue(arr.get(i), childPath(path, String.valueOf(i)));
        } else {
            addField(path, value);
        }
    }

    private void addField(String path, Object value) {
        String type = value == JSONObject.NULL ? "null" :
                value instanceof JSONObject ? "object" :
                value instanceof JSONArray ? "array" :
                value instanceof Boolean ? "boolean" :
                value instanceof Number ? "number" : "string";
        boolean editable = "number".equals(type) || "boolean".equals(type) || "string".equals(type);
        String shown;
        if (value == JSONObject.NULL) shown = "null";
        else if (value instanceof JSONObject) shown = "كائن • " + ((JSONObject)value).length() + " مفاتيح";
        else if (value instanceof JSONArray) shown = "مصفوفة • " + ((JSONArray)value).length() + " عناصر";
        else shown = String.valueOf(value);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(11), dp(7), dp(11), dp(8));
        GradientDrawable card = new GradientDrawable();
        card.setColor(("object".equals(type) || "array".equals(type)) ? 0xFF173544 : 0xFF102633);
        card.setCornerRadius(dp(13));
        card.setStroke(dp(1), ("object".equals(type) || "array".equals(type)) ? 0xFF386174 : 0xFF243F4D);
        row.setBackground(card);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(6);
        row.setLayoutParams(rowParams);

        TextView label = text(path.isEmpty() ? "playerData / الجذر" : path, 12);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(0xFFB8E9E4);
        label.setTextIsSelectable(true);
        row.addView(label);

        EditText input = null;
        if (editable) {
            input = new EditText(this);
            input.setSingleLine(false);
            input.setTextSize(14);
            input.setTextColor(0xFFF1F6F8);
            input.setHintTextColor(0xFF8CA7B2);
            input.setText(shown);
            input.setHint(type);
            input.setPadding(dp(9), dp(2), dp(9), dp(2));
            GradientDrawable inputBg = new GradientDrawable();
            inputBg.setColor(0xFF0A1B26);
            inputBg.setCornerRadius(dp(8));
            inputBg.setStroke(dp(1), 0xFF294957);
            input.setBackground(inputBg);
            if ("number".equals(type)) {
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            } else if ("boolean".equals(type)) {
                input.setInputType(InputType.TYPE_CLASS_TEXT);
            } else {
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            }
            row.addView(input);
        } else {
            TextView preview = text(shown, 13);
            preview.setTextColor(0xFFE2EDF1);
            row.addView(preview);
        }
        TextView typeHint = text("النوع: " + type + (editable ? " • قابل للتعديل" : " • بنية/عرض فقط"), 10);
        typeHint.setTextColor(0xFF8EAAB6);
        row.addView(typeHint);
        fieldContainer.addView(row);
        fields.add(new Field(path, type, shown, input, label, row, editable));
    }

    private void filterFields(String query) {
        if (fieldContainer == null) return;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int visible = 0;
        for (Field f : fields) {
            boolean match = q.isEmpty() || f.path.toLowerCase(Locale.ROOT).contains(q)
                    || f.type.toLowerCase(Locale.ROOT).contains(q)
                    || f.originalValue.toLowerCase(Locale.ROOT).contains(q);
            f.row.setVisibility(match ? View.VISIBLE : View.GONE);
            if (match) visible++;
        }
        if (saveJson != null) status.setText("المعروض " + visible + " من " + fields.size() + " حقل. البحث لا يحذف أي بيانات.");
    }

    private Object getPathValue(Object root, String path) throws JSONException {
        if (path.isEmpty()) return root;
        String[] parts = path.substring(1).split("/", -1);
        Object current = root;
        for (String raw : parts) {
            String part = raw.replace("~1", "/").replace("~0", "~");
            if (current instanceof JSONObject) current = ((JSONObject)current).get(part);
            else if (current instanceof JSONArray) current = ((JSONArray)current).get(Integer.parseInt(part));
            else throw new JSONException("مسار غير صالح: " + path);
        }
        return current;
    }

    private void setPathValue(Object root, String path, Object value) throws JSONException {
        if (path.isEmpty()) throw new JSONException("لا يمكن استبدال جذر الملف");
        String[] parts = path.substring(1).split("/", -1);
        Object current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i].replace("~1", "/").replace("~0", "~");
            if (current instanceof JSONObject) current = ((JSONObject)current).get(part);
            else if (current instanceof JSONArray) current = ((JSONArray)current).get(Integer.parseInt(part));
            else throw new JSONException("مسار غير صالح: " + path);
        }
        String leaf = parts[parts.length - 1].replace("~1", "/").replace("~0", "~");
        if (current instanceof JSONObject) ((JSONObject)current).put(leaf, value);
        else if (current instanceof JSONArray) ((JSONArray)current).put(Integer.parseInt(leaf), value);
        else throw new JSONException("مسار غير صالح: " + path);
    }

    private Object parseEditedValue(Field f, String raw) {
        if ("number".equals(f.type)) {
            String s = raw.trim();
            if (s.isEmpty()) throw new IllegalArgumentException("قيمة رقمية فارغة في " + f.path);
            Object old = null;
            try { old = getPathValue(saveJson, f.path); } catch (Exception ignored) {}
            if (old instanceof Integer) return Integer.valueOf(s);
            if (old instanceof Long) return Long.valueOf(s);
            if (old instanceof Float) return Float.valueOf(s);
            if (old instanceof Double) return Double.valueOf(s);
            try { return Integer.valueOf(s); } catch (NumberFormatException ignored) {}
            try { return Long.valueOf(s); } catch (NumberFormatException ignored) {}
            return Double.valueOf(s);
        }
        if ("boolean".equals(f.type)) {
            String s = raw.trim().toLowerCase(Locale.ROOT);
            if (!"true".equals(s) && !"false".equals(s)) throw new IllegalArgumentException("اكتب true أو false في " + f.path);
            return Boolean.valueOf(s);
        }
        if ("string".equals(f.type)) return raw;
        return JSONObject.NULL;
    }

    private void exportEdited() {
        if (saveJson == null) { toast("اختر ملف الحفظ أولًا"); return; }
        try {
            JSONObject edited = new JSONObject(saveJson.toString());
            int changed = 0;
            for (Field f : fields) {
                if (!f.editable || f.input == null) continue;
                String raw = f.input.getText().toString();
                Object next = parseEditedValue(f, raw);
                Object old = getPathValue(edited, f.path);
                if (!String.valueOf(old).equals(String.valueOf(next))) {
                    setPathValue(edited, f.path, next);
                    changed++;
                }
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
            status.setText("تم تجهيز ملف جديد. عدد الحقول التي تغيرت: " + changed + ". لا تستبدل الأصل.");
        } catch (Exception e) {
            toast("تعذر تجهيز الملف: " + e.getMessage());
        }
    }

    private void exportOriginal() {
        if (originalBytes == null) { toast("اختر ملف الحفظ أولًا"); return; }
        exportBytes = originalBytes.clone();
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE, "user1_backup.dat");
        startActivityForResult(i, CREATE_BACKUP);
    }

    private void writeExport(Uri uri) throws Exception {
        if (exportBytes == null) throw new IllegalStateException("لا توجد بيانات جاهزة للتصدير");
        try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
            if (out == null) throw new IllegalStateException("تعذر إنشاء الملف");
            out.write(exportBytes); out.flush();
        }
        status.setText("اكتمل التصدير. احتفظ بالأصل واختبر الملف الجديد داخل اللعبة قبل اعتماده.");
        toast("تم تصدير الملف");
        exportBytes = null;
    }
}
