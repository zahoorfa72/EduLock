package com.edulock.app

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

private val urduUi = mapOf(
    "Welcome to EduLock ✨" to "EduLock میں خوش آمدید ✨",
    "Learn. Explore. Grow." to "سیکھیں، دریافت کریں، آگے بڑھیں",
    "Learn first. Play with purpose." to "پہلے سیکھیں، مقصد کے ساتھ کھیلیں",
    "Create your parent account" to "والدین کا اکاؤنٹ بنائیں",
    "Welcome back" to "دوبارہ خوش آمدید",
    "Parent name" to "والد/والدہ کا نام",
    "Parent email" to "والدین کا ای میل",
    "Password (minimum 8 characters)" to "پاس ورڈ (کم از کم 8 حروف)",
    "Confirm password" to "پاس ورڈ کی تصدیق کریں",
    "Passwords do not match yet." to "دونوں پاس ورڈ ایک جیسے نہیں ہیں",
    "Create parent account" to "والدین کا اکاؤنٹ بنائیں",
    "Sign in as parent" to "والدین کے طور پر لاگ اِن کریں",
    "Already have an account? Sign in" to "اکاؤنٹ پہلے سے ہے؟ لاگ اِن کریں",
    "New to EduLock? Create an account" to "EduLock پر نئے ہیں؟ اکاؤنٹ بنائیں",
    "This is the parent's phone" to "یہ والدین کا فون ہے",
    "This is the child's phone — pair with code" to "یہ بچے کا فون ہے — کوڈ سے جوڑیں",
    "Set up a child's device with pairing code" to "بچے کا فون جوڑنے کے لیے کوڈ استعمال کریں",
    "Parent dashboard" to "والدین کا ڈیش بورڈ",
    "Manage child profiles" to "بچوں کے پروفائلز",
    "Subjects & questions" to "مضامین اور سوالات",
    "Learning templates for all subjects" to "تمام مضامین کے سوالات کے نمونے",
    "3D Learning Lab • all models" to "تھری ڈی لرننگ لیب • تمام ماڈلز",
    "Drawing & Colour Studio" to "ڈرائنگ اور رنگوں کا اسٹوڈیو",
    "Assign learning task & reward" to "تعلیمی کام اور انعام دیں",
    "Learning progress reports" to "تعلیمی کارکردگی کی رپورٹ",
    "Family settings • country, religion & app language" to "خاندانی ترتیبات • ملک، دینی نصاب اور ایپ کی زبان",
    "Family learning settings" to "خاندانی تعلیمی ترتیبات",
    "Done" to "مکمل",
    "Close" to "بند کریں",
    "Cancel" to "منسوخ",
    "Choose a country" to "ملک منتخب کریں",
    "Search countries" to "ملک تلاش کریں",
    "Choose a learning language" to "تعلیم کی زبان منتخب کریں",
    "Search language or code" to "زبان یا کوڈ تلاش کریں",
    "Choose a religious-education curriculum" to "دینی تعلیم کا نصاب منتخب کریں",
    "Religious education" to "دینی تعلیم",
    "Choose a profile icon" to "پروفائل کی تصویر منتخب کریں",
    "Save educational game limit" to "تعلیمی کھیل کا وقت محفوظ کریں",
    "Your learning space" to "آپ کی تعلیمی جگہ",
    "Ready for today's adventure?" to "آج کے سیکھنے کے سفر کے لیے تیار؟",
    "🎮  Learning Games • daily time limit" to "🎮 تعلیمی کھیل • روزانہ وقت کی حد",
    "🎨 Draw & colour" to "🎨 ڈرائنگ اور رنگ",
    "Kids' learning arcade" to "بچوں کے تعلیمی کھیل",
    "Choose a drawing challenge" to "ڈرائنگ کا چیلنج منتخب کریں",
    "Free draw" to "آزاد ڈرائنگ",
    "Sun & sky" to "سورج اور آسمان",
    "House" to "گھر",
    "Flower" to "پھول",
    "Pick a colour" to "رنگ منتخب کریں",
    "Fine pen" to "باریک قلم",
    "Brush" to "برش",
    "Thick" to "موٹا",
    "Undo" to "واپس کریں",
    "Clear page" to "صفحہ صاف کریں",
    "Done drawing" to "ڈرائنگ مکمل",
    "Math Dash" to "ریاضی ریس",
    "Science Safari" to "سائنس سفاری",
    "Word Wizard" to "الفاظ کا جادو",
    "Memory & Patterns" to "یادداشت اور نمونے",
    "Family location & offline alerts" to "خاندانی مقام اور آف لائن اطلاع",
    "Allowed apps & screen-time settings" to "اجازت یافتہ ایپس اور اسکرین ٹائم",
    "Sign out" to "لاگ آؤٹ",
    "Save learning profile" to "تعلیمی پروفائل محفوظ کریں",
    "Country syllabus & question bank" to "ملکی نصاب اور سوالات",
    "Generate pairing code" to "جوڑنے کا کوڈ بنائیں",
    "Daily educational game limit in minutes (0 disables games)" to "روزانہ تعلیمی کھیلوں کے منٹ (0 سے کھیل بند)",
    "Religious-education preference" to "دینی تعلیم کا انتخاب",
    "App display language" to "ایپ کی زبان",
    "Curriculum country" to "نصاب کا ملک",
    "Preferred lesson language" to "تعلیم کی پسندیدہ زبان"
)

private val hindiUi = mapOf(
    "Welcome to EduLock ✨" to "EduLock में आपका स्वागत है ✨",
    "Learn. Explore. Grow." to "सीखें। खोजें। आगे बढ़ें।",
    "Learn first. Play with purpose." to "पहले सीखें, उद्देश्य के साथ खेलें",
    "Create your parent account" to "अभिभावक खाता बनाएँ",
    "Welcome back" to "वापसी पर स्वागत है",
    "Parent name" to "अभिभावक का नाम",
    "Parent email" to "अभिभावक का ईमेल",
    "Password (minimum 8 characters)" to "पासवर्ड (कम से कम 8 अक्षर)",
    "Confirm password" to "पासवर्ड की पुष्टि करें",
    "Passwords do not match yet." to "दोनों पासवर्ड मेल नहीं खाते",
    "Create parent account" to "अभिभावक खाता बनाएँ",
    "Sign in as parent" to "अभिभावक के रूप में लॉग इन करें",
    "Already have an account? Sign in" to "खाता है? लॉग इन करें",
    "New to EduLock? Create an account" to "EduLock पर नए हैं? खाता बनाएँ",
    "This is the parent's phone" to "यह अभिभावक का फ़ोन है",
    "This is the child's phone — pair with code" to "यह बच्चे का फ़ोन है — कोड से जोड़ें",
    "Parent dashboard" to "अभिभावक डैशबोर्ड",
    "Manage child profiles" to "बच्चों की प्रोफ़ाइल प्रबंधित करें",
    "Subjects & questions" to "विषय और प्रश्न",
    "Learning templates for all subjects" to "सभी विषयों के प्रश्न नमूने",
    "3D Learning Lab • all models" to "3D लर्निंग लैब • सभी मॉडल",
    "Drawing & Colour Studio" to "चित्रकारी और रंग स्टूडियो",
    "Assign learning task & reward" to "सीखने का कार्य और पुरस्कार दें",
    "Family settings • country, religion & app language" to "परिवार सेटिंग • देश, धार्मिक पाठ्यक्रम और ऐप भाषा",
    "Family learning settings" to "परिवार की सीखने की सेटिंग",
    "Done" to "हो गया",
    "Close" to "बंद करें",
    "Cancel" to "रद्द करें",
    "Choose a country" to "देश चुनें",
    "Search countries" to "देश खोजें",
    "Choose a learning language" to "सीखने की भाषा चुनें",
    "Search language or code" to "भाषा या कोड खोजें",
    "Choose a religious-education curriculum" to "धार्मिक शिक्षा का पाठ्यक्रम चुनें",
    "Your learning space" to "आपकी सीखने की जगह",
    "Ready for today's adventure?" to "आज के सीखने के सफ़र के लिए तैयार?",
    "🎮  Learning Games • daily time limit" to "🎮 सीखने के खेल • दैनिक समय सीमा",
    "🎨 Draw & colour" to "🎨 चित्र बनाएँ और रंग भरें",
    "Choose a drawing challenge" to "चित्रकारी चुनौती चुनें",
    "Free draw" to "मुक्त चित्रकारी",
    "Sun & sky" to "सूरज और आकाश",
    "House" to "घर",
    "Flower" to "फूल",
    "Pick a colour" to "रंग चुनें",
    "Fine pen" to "बारीक पेन",
    "Brush" to "ब्रश",
    "Thick" to "मोटा",
    "Undo" to "वापस करें",
    "Clear page" to "पृष्ठ साफ़ करें",
    "Done drawing" to "चित्र पूरा",
    "Math Dash" to "गणित दौड़",
    "Science Safari" to "विज्ञान सफ़ारी",
    "Word Wizard" to "शब्द जादू",
    "Memory & Patterns" to "स्मृति और पैटर्न",
    "Sign out" to "लॉग आउट",
    "Save learning profile" to "सीखने की प्रोफ़ाइल सहेजें",
    "Religious-education preference" to "धार्मिक शिक्षा विकल्प",
    "App display language" to "ऐप की भाषा",
    "Curriculum country" to "पाठ्यक्रम का देश",
    "Preferred lesson language" to "पसंदीदा सीखने की भाषा"
)

private val sindhiUi = mapOf(
    "Welcome to EduLock ✨" to "EduLock ۾ ڀليڪار ✨",
    "Learn. Explore. Grow." to "سکو، ڳوليو، اڳتي وڌو",
    "Learn first. Play with purpose." to "پهريان سکو، مقصد سان کيڏو",
    "Create your parent account" to "والدين جو اڪائونٽ ٺاهيو",
    "Welcome back" to "واپسي تي ڀليڪار",
    "Parent name" to "والدين جو نالو",
    "Parent email" to "والدين جي اي ميل",
    "Password (minimum 8 characters)" to "پاسورڊ (گهٽ ۾ گهٽ 8 اکر)",
    "Confirm password" to "پاسورڊ جي تصديق ڪريو",
    "Passwords do not match yet." to "ٻئي پاسورڊ هڪجهڙا ناهن",
    "Create parent account" to "والدين جو اڪائونٽ ٺاهيو",
    "Sign in as parent" to "والدين طور لاگ ان ٿيو",
    "Already have an account? Sign in" to "اڪائونٽ اڳ ۾ آهي؟ لاگ ان ٿيو",
    "New to EduLock? Create an account" to "EduLock تي نوان آهيو؟ اڪائونٽ ٺاهيو",
    "This is the parent's phone" to "هي والدين جو فون آهي",
    "This is the child's phone — pair with code" to "هي ٻار جو فون آهي — ڪوڊ سان ڳنڍيو",
    "Parent dashboard" to "والدين جو ڊيش بورڊ",
    "Manage child profiles" to "ٻارن جا پروفائيل سنڀاليو",
    "Subjects & questions" to "مضمون ۽ سوال",
    "Learning templates for all subjects" to "سڀني مضمونن جا سوالن جا نمونا",
    "3D Learning Lab • all models" to "3D سکيا ليب • سڀ ماڊل",
    "Drawing & Colour Studio" to "ڊرائنگ ۽ رنگن جو اسٽوڊيو",
    "Assign learning task & reward" to "سکيا جو ڪم ۽ انعام ڏيو",
    "Family learning settings" to "خاندان جي سکيا جون سيٽنگون",
    "Done" to "ٿي ويو",
    "Close" to "بند ڪريو",
    "Cancel" to "منسوخ ڪريو",
    "Choose a country" to "ملڪ چونڊيو",
    "Search countries" to "ملڪ ڳوليو",
    "Choose a learning language" to "سکيا جي ٻولي چونڊيو",
    "Search language or code" to "ٻولي يا ڪوڊ ڳوليو",
    "Your learning space" to "توهان جي سکيا جي جاءِ",
    "Ready for today's adventure?" to "اڄ جي سکيا لاءِ تيار آهيو؟",
    "🎨 Draw & colour" to "🎨 تصوير ٺاهيو ۽ رنگ ڀريو",
    "Choose a drawing challenge" to "ڊرائنگ جو چئلينج چونڊيو",
    "Free draw" to "آزاد ڊرائنگ",
    "Sun & sky" to "سج ۽ آسمان",
    "House" to "گهر",
    "Flower" to "گل",
    "Pick a colour" to "رنگ چونڊيو",
    "Fine pen" to "سنهو قلم",
    "Brush" to "برش",
    "Thick" to "ٿلهو",
    "Undo" to "واپس ڪريو",
    "Clear page" to "صفحو صاف ڪريو",
    "Done drawing" to "ڊرائنگ مڪمل",
    "Sign out" to "لاگ آئوٽ",
    "Save learning profile" to "سکيا جو پروفائيل محفوظ ڪريو",
    "App display language" to "ايپ جي ٻولي",
    "Curriculum country" to "نصاب جو ملڪ",
    "Preferred lesson language" to "پسنديده سکيا جي ٻولي"
)

fun translateEduLockUiText(text: String, languageCode: String): String {
    val normalized = languageCode.lowercase().substringBefore('-')
    val dictionary = when (normalized) {
        "ur" -> urduUi
        "hi" -> hindiUi
        "sd" -> sindhiUi
        else -> emptyMap()
    }
    if (dictionary.isEmpty()) return text
    dictionary[text]?.let { return it }
    var translated = text
    dictionary.entries.sortedByDescending { it.key.length }.forEach { (source, target) ->
        if (source.length > 3 && translated.contains(source)) translated = translated.replace(source, target)
    }
    return translated
}

@Composable
fun AppUiText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current
) {
    val context = LocalContext.current
    val language = context.getSharedPreferences("edulock_display_language", Context.MODE_PRIVATE)
        .getString("language", "en") ?: "en"
    Text(
        text = translateEduLockUiText(text, language),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        style = style
    )
}
