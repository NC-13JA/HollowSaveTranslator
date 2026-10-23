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
    private Button loadMoreButton;
    private final ArrayList<Field> filteredFields = new ArrayList<>();
    private int renderedCount = 0;
    private static final int PAGE_SIZE = 80;
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
        root.setBackgroundResource(com.nc13ja.hollowsavetranslator.R.drawable.bg_hallownight);

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
        filteredFields.clear();
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
            if (value == JSONObject.NULL) nullCount++;
            addField(path, value);
        }
    }

    private String fieldDescription(String path, String type) {
        String key = path;
        if (key.contains("/")) key = key.substring(key.lastIndexOf("/") + 1);
        key = key.replace("~1", "/").replace("~0", "~");
        String k = key.toLowerCase(Locale.ROOT);

        if ("geo".equals(k)) return "المال (Geo): العملة التي تستخدمها للشراء من التجار. رفعها يزيد رصيدك؛ لا تغيّرها إلى قيمة سالبة.";
        if ("health".equals(k)) return "الصحة الحالية: عدد الأقنعة/نقاط الضرر التي يستطيع الفارس تحمّلها حاليًا.";
        if ("maxhealth".equals(k)) return "الحد الأقصى للصحة. قد تعتمد الزيادة الصحيحة على أقنعة الصحة المكتسبة أيضًا.";
        if ("hasdash".equals(k)) return "قدرة الاندفاع (Mothwing Cloak): true تعني أن القدرة مسجّلة كمكتسبة، وfalse تعني أنها غير مكتسبة.";
        if ("haswalljump".equals(k)) return "القفز على الجدران (Mantis Claw): true تعني أن القدرة مسجّلة كمكتسبة.";
        if ("hasdoublejump".equals(k)) return "القفزة الإضافية (Monarch Wings): true تعني أن القدرة مسجّلة كمكتسبة.";
        if ("haslantern".equals(k)) return "فانوس Lumafly: يساعد على رؤية بعض المناطق المظلمة عند امتلاكه.";
        if ("hasdreamnail".equals(k) || "dreamnail".equals(k)) return "مسمار الأحلام: حالة الحصول على هذه الأداة أو بياناتها.";
        if ("naildamage".equals(k)) return "ضرر السيف (Nail) المسجّل. قد توجد قيم أخرى مرتبطة بترقية السيف.";
        if ("mpcharge".equals(k)) return "كمية طاقة الروح المتاحة/المخزنة.";
        if ("mpreserve".equals(k)) return "مخزون الروح الإضافي.";
        if ("essence".equals(k)) return "كمية الجوهر (Essence) التي يجمعها اللاعب.";
        if ("playtime".equals(k)) return "مدة اللعب المسجلة في ملف الحفظ.";
        if ("maxhealthbase".equals(k)) return "قيمة الصحة الأساسية القصوى قبل احتساب بعض الترقيات.";
        if ("health".equals(k)) return "عدد نقاط الصحة الحالية.";
        if ("maxhealth".equals(k)) return "الحد الأقصى لنقاط الصحة.";
        if ("playtime".equals(k)) return "مدة اللعب المسجلة في ملف الحفظ.";
        if ("maxhealthbase".equals(k)) return "قيمة الصحة الأساسية القصوى.";
        if ("MPCharge".equalsIgnoreCase(key) || "mpcharge".equals(k)) return "طاقة الروح المتاحة أو المخزنة.";
        if ("MPReserve".equalsIgnoreCase(key) || "mpreserve".equals(k)) return "مخزون الروح الإضافي.";
        if ("hasmap".equals(k) || "map".equals(k)) return "حالة امتلاك الخريطة أو بياناتها.";
        if ("equippedcharms".equals(k)) return "قائمة التعويذات المجهزة حاليًا.";
        if ("charms".equals(k)) return "بيانات التعويذات التي جُمعت أو فُتحت.";
        if ("naildamage".equals(k)) return "قيمة الضرر الذي يسببه السيف (Nail).";
        if ("nail".equals(k)) return "حالة السيف أو بيانات تطويره.";
        if ("maskshards".equals(k)) return "عدد شظايا الأقنعة التي جُمعت.";
        if ("vesselfragments".equals(k)) return "عدد شظايا وعاء الروح التي جُمعت.";
        if ("grubs".equals(k)) return "بيانات اليرقات التي تم العثور عليها أو إنقاذها.";
        if ("essence".equals(k)) return "كمية الجوهر (Essence) المسجلة.";
        if ("dreamnail".equals(k)) return "حالة الحصول على مسمار الأحلام.";
        if ("hasdash".equals(k) || "dash".equals(k)) return "هل قدرة الاندفاع مفتوحة.";
        if ("haswalljump".equals(k) || "walljump".equals(k)) return "هل القفز على الجدران مفتوح.";
        if ("hasdoublejump".equals(k) || "doublejump".equals(k)) return "هل القفزة الإضافية مفتوحة.";
        if ("haswalljump".equals(k)) return "هل القدرة على القفز من الجدار متاحة.";
        if ("haslantern".equals(k) || "lantern".equals(k)) return "حالة امتلاك الفانوس.";
        if ("stag".equals(k) || k.contains("stagstation")) return "حالة محطة عربة الـ Stag للتنقل السريع.";
        if ("godseekerunlocked".equals(k)) return "هل تم فتح محتوى Godseeker.";
        if ("radiance".equals(k)) return "بيانات مرتبطة بزعيم Radiance.";
        if ("bossrushmode".equals(k)) return "حالة طور تحديات الزعماء.";
        if ("currentbosssequence".equals(k)) return "بيانات تسلسل تحدي الزعماء الحالي.";
        if ("scenedata".equals(k)) return "مجموعة بيانات تغيّر حالة العناصر داخل مشاهد اللعبة.";
        if ("georocks".equals(k)) return "سجلات صخور المال الموجودة في المشاهد.";
        if ("persistentboolitems".equals(k)) return "حالات تشغيل/تفعيل العناصر المحفوظة في المشاهد.";
        if ("persistentintitems".equals(k)) return "قيم رقمية محفوظة لعناصر المشاهد.";
        if ("viewedbossscenecompletions".equals(k)) return "قائمة مشاهد إكمال الزعماء التي سُجلت كمشاهدة.";
        if (k.startsWith("killed")) return "هل تم تسجيل هزيمة هذا العدو أو الزعيم.";
        if (k.startsWith("kills")) return "عدد مرات قتل هذا العدو المسجلة.";
        if (k.startsWith("newdata")) return "هل تم تسجيل بيانات/مدخل جديد لهذا العدو.";
        if (k.startsWith("statueState".toLowerCase(Locale.ROOT))) return "حالة تمثال الزعيم: الظهور والفتح ومستويات الإكمال.";
        if (k.startsWith("completedtier")) return "هل اكتمل مستوى التحدي المحدد.";
        if (k.startsWith("unlocked") || k.startsWith("has") || k.startsWith("got") || k.startsWith("opened")) return "يوضح هل تم الحصول على هذا الشيء أو فتحه.";
        if (k.startsWith("seen") || k.startsWith("viewed")) return "يوضح هل شاهد اللاعب هذا المشهد أو العنصر.";
        if (k.startsWith("given")) return "يوضح هل تم تسليم هذا العنصر لشخصية.";
        if (k.startsWith("health")) return "قيمة مرتبطة بصحة اللاعب.";
        if (k.contains("charm")) return "بيانات مرتبطة بالتعويذات وامتلاكها أو تجهيزها.";
        if (k.contains("nail")) return "بيانات مرتبطة بالسيف أو تطويره أو استخدامه.";
        if (k.contains("soul") || k.contains("mpcharge")) return "بيانات مرتبطة بطاقة الروح.";
        if (k.contains("mask") || k.contains("health")) return "بيانات مرتبطة بالصحة أو الأقنعة.";
        if (k.contains("grub")) return "بيانات مرتبطة باليرقات وإنقاذها.";
        if (k.contains("boss") || k.contains("statue")) return "بيانات مرتبطة بالزعماء أو تحدياتهم.";
        if (k.contains("map") || k.contains("mapzone")) return "بيانات مرتبطة بالخرائط أو المناطق.";
        if (k.contains("scene") || k.contains("door") || k.contains("lever")) return "حالة عنصر أو حدث داخل أحد مشاهد اللعبة.";
        if (k.contains("dream")) return "بيانات مرتبطة بعناصر أو قدرات الأحلام.";
        if (k.contains("geo")) return "بيانات مرتبطة بالمال (Geo) أو مصادره.";
        if ("object".equals(type)) return "مجموعة حقول فرعية؛ افتحها عبر البحث عن اسم الحقل أو مساره.";
        if ("array".equals(type)) return "قائمة عناصر محفوظة؛ ابحث باسمها أو برقم العنصر في المسار.";
        if ("boolean".equals(type)) {
            String readable = humanizeFieldName(key);
            if (k.startsWith("has") || k.startsWith("got") || k.startsWith("unlocked") || k.startsWith("opened"))
                return "حالة امتلاك/فتح مرتبطة بـ«" + readable + "». true تعني أن اللعبة تسجّلها مفتوحة أو مملوكة، وfalse تعني غير ذلك. هذا استنتاج من الاسم، وليس ضمانًا لأثر التعديل.";
            if (k.startsWith("killed") || k.startsWith("defeated"))
                return "حالة هزيمة مرتبطة بـ«" + readable + "». true تعني أن الهزيمة مسجّلة، وfalse تعني أنها غير مسجّلة.";
            if (k.startsWith("seen") || k.startsWith("viewed"))
                return "حالة مشاهدة مرتبطة بـ«" + readable + "». true تعني أن المشاهدة مسجّلة، وfalse تعني أنها غير مسجّلة.";
            return "قيمة نعم/لا تخص «" + readable + "». true = نعم، وfalse = لا؛ لكن الاسم وحده لا يكفي لمعرفة أثرها داخل اللعبة، لذلك لا تغيّرها إلا بعد تحديد وظيفة هذا المسار.";
        }
        if ("number".equals(type)) return describeNumber(k, key);
        if ("string".equals(type)) return describeString(k, key);
        if ("null".equals(type)) return "قيمة فارغة محفوظة كما هي.";
        return "معلومة داخلية من بيانات حفظ اللعبة.";
    }

    private String humanizeFieldName(String key) {
        String s = key.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').replace('-', ' ');
        String low = s.toLowerCase(Locale.ROOT);
        String[][] words = {
            {"health","الصحة"},{"geo","المال Geo"},{"charm","تعويذة"},{"nail","السيف"},{"damage","الضرر"},
            {"dash","الاندفاع"},{"wall jump","القفز على الجدران"},{"double jump","القفزة الإضافية"},
            {"grub","يرقة"},{"boss","زعيم"},{"killed","هُزم"},{"defeated","هُزم"},{"unlocked","مفتوح"},
            {"opened","فُتح"},{"got","تم الحصول عليه"},{"has","يمتلك/لديه"},{"seen","شوهد"},{"viewed","تمت مشاهدته"},
            {"map","الخريطة"},{"dream","الأحلام"},{"soul","الروح"},{"mask","قناع"},{"vessel","وعاء الروح"},
            {"station","محطة"},{"gate","بوابة"},{"door","باب"},{"lever","رافعة"},{"item","عنصر"},
            {"scene","المشهد"},{"radiance","Radiance"},{"essence","الجوهر"},{"playtime","مدة اللعب"}
        };
        for (String[] pair : words) low = low.replace(pair[0], pair[1]);
        return low;
    }

    private String describeNumber(String k, String key) {
        if (k.contains("geo")) return "عدد وحدات المال (Geo) المسجّل في هذا الموضع.";
        if (k.contains("health") || k.contains("mask")) return "عدد/قيمة مرتبطة بالصحة أو الأقنعة. راجع الحقول المجاورة لأن الحد الأقصى قد يعتمد على أكثر من قيمة.";
        if (k.contains("essence")) return "كمية الجوهر (Essence) المسجّلة.";
        if (k.contains("nail") && (k.contains("damage") || k.contains("level"))) return "قيمة ضرر السيف أو مستوى تطويره؛ قد لا يكفي تغيير هذا الحقل وحده.";
        if (k.contains("kill")) return "عداد مرات هزيمة العدو/الزعيم المسجّلة.";
        if (k.contains("grub")) return "عدد أو حالة رقمية مرتبطة باليرقات التي تم العثور عليها/إنقاذها.";
        if (k.contains("charm")) return "رقم داخلي مرتبط بالتعويذات. معنى الرقم الدقيق يعتمد على اسم الحقل والمسار.";
        if (k.contains("mpcharge") || k.contains("soul")) return "كمية أو سعة مرتبطة بطاقة الروح.";
        if (k.contains("playtime")) return "مدة اللعب المسجّلة؛ قد تكون الوحدة داخلية وليست دقائق مباشرة.";
        if (k.contains("scene") || k.contains("statue") || k.contains("boss")) return "قيمة حالة/عداد مرتبطة بالمشهد أو الزعيم. لا تغيّر الرقم قبل تحديد معنى هذا المسار.";
        if (k.equals("x") || k.equals("y") || k.contains("position")) return "قد تكون إحداثية مكانية داخل مشهد اللعبة؛ تعديلها قد ينقل اللاعب أو عنصرًا إلى مكان غير صالح.";
        return "قيمة رقمية داخلية باسم «" + humanizeFieldName(key) + "». لا يمكن تحديد معناها الدقيق من الاسم وحده؛ اتركها كما هي حتى يتوفر تفسير موثوق لهذا المسار.";
    }

    private String describeString(String k, String key) {
        if (k.contains("scene")) return "اسم/معرّف مشهد تستخدمه اللعبة لتحديد المنطقة أو مكان تسجيل الحالة. تغييره قد يجعل الحفظ يشير إلى مشهد غير صحيح.";
        if (k.contains("name")) return "نص أو اسم محفوظ. إذا كان معرّفًا داخليًا فقد تتعطل الإشارة إلى العنصر عند تغييره.";
        if (k.contains("id") || k.endsWith("key")) return "معرّف داخلي للعبة، وليس بالضرورة نصًا معروضًا للاعب. لا تغيّره عشوائيًا.";
        return "نص محفوظ باسم «" + humanizeFieldName(key) + "». لا يمكن تحديد استخدامه الدقيق من الاسم وحده؛ اتركه كما هو إن لم تعرف مرجعه.";
    }

    // Defer view creation until a field is actually shown; large saves stay responsive.
    private void addField(String path, Object value) {
        String type = value == JSONObject.NULL ? "null" :
                value instanceof JSONObject ? "object" :
                value instanceof JSONArray ? "array" :
                value instanceof Boolean ? "boolean" :
                value instanceof Number ? "number" : "string";
        String shown;
        if (value == JSONObject.NULL) shown = "null";
        else if (value instanceof JSONObject) shown = "كائن • " + ((JSONObject)value).length() + " مفاتيح";
        else if (value instanceof JSONArray) shown = "مصفوفة • " + ((JSONArray)value).length() + " عناصر";
        else shown = String.valueOf(value);
        boolean editable = "number".equals(type) || "boolean".equals(type) || "string".equals(type);
        fields.add(new Field(path, type, shown, null, null, null, editable));
    }

    private void createFieldView(Field f) {
        String path = f.path, type = f.type, shown = f.originalValue;
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
        TextView explanation = text(fieldDescription(path, type), 12);
        explanation.setTextColor(0xFFD2E0E6);
        explanation.setPadding(0, 0, 0, dp(5));
        row.addView(explanation);
        EditText input = null;
        if (f.editable) {
            input = new EditText(this);
            input.setSingleLine(!"string".equals(type));
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
                input.setHint("اكتب true أو false");
            } else {
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            }
            row.addView(input);
        } else {
            TextView preview = text(shown, 13);
            preview.setTextColor(0xFFE2EDF1);
            row.addView(preview);
        }
        TextView typeHint = text("النوع: " + type + (f.editable ? " • قابل للتعديل" : " • بنية للعرض فقط"), 10);
        typeHint.setTextColor(0xFF8EAAB6);
        row.addView(typeHint);
        f.input = input; f.label = label; f.row = row;
    }

    private void filterFields(String query) {
        if (fieldContainer == null) return;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredFields.clear();
        for (Field f : fields) {
            String desc = fieldDescription(f.path, f.type).toLowerCase(Locale.ROOT);
            boolean match = q.isEmpty() || f.path.toLowerCase(Locale.ROOT).contains(q)
                    || f.type.toLowerCase(Locale.ROOT).contains(q)
                    || f.originalValue.toLowerCase(Locale.ROOT).contains(q)
                    || desc.contains(q);
            if (match) filteredFields.add(f);
        }
        fieldContainer.removeAllViews();
        renderedCount = 0;
        renderNextPage();
        if (saveJson != null) status.setText("المعروض " + Math.min(renderedCount, filteredFields.size()) +
                " من " + filteredFields.size() + " نتيجة مطابقة (" + fields.size() + " حقل إجمالًا).");
    }

    private void renderNextPage() {
        int end = Math.min(renderedCount + PAGE_SIZE, filteredFields.size());
        for (int i = renderedCount; i < end; i++) {
            Field f = filteredFields.get(i);
            if (f.row == null) createFieldView(f);
            fieldContainer.addView(f.row);
        }
        renderedCount = end;
        if (renderedCount < filteredFields.size()) {
            if (loadMoreButton == null) {
                loadMoreButton = makeButton("عرض 80 حقلًا إضافيًا", false);
                loadMoreButton.setOnClickListener(v -> {
                    fieldContainer.removeView(loadMoreButton);
                    renderNextPage();
                });
            }
            fieldContainer.addView(loadMoreButton, new LinearLayout.LayoutParams(-1, dp(46)));
        }
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

    // Hollow Knight's C# save wrapper stores the Base64 payload length as a 7-bit encoded integer.
    private byte[] cSharpLength(int length) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(5);
        int value = length;
        while (value >= 0x80) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value & 0x7F);
        return out.toByteArray();
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
            out.write(HEADER); out.write(cSharpLength(encoded.length)); out.write(encoded); out.write(11);
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
