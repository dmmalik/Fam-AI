package ai.fam.app.data

/**
 * Care steps for leaf classes that are not in the shared treatment list.
 * Classes the knowledge base already knows keep that list instead.
 */
object LeafCare {
    fun forClass(id: String, language: String): AdviceText {
        val copy = when {
            id.endsWith("_healthy") -> healthy
            id.contains("powdery") -> powdery
            id.contains("bacterial") -> bacterial
            id == "citrus_greening" -> greening
            id == "grape_esca" -> esca
            id == "grape_black_rot" -> blackRot
            id == "grape_leaf_blight" -> leafBlight
            id == "strawberry_leaf_scorch" -> scorch
            else -> problem
        }
        return copy.inLanguage(language)
    }

    private val healthy = pack(
        en = copy(
            "This leaf looks healthy. Keep the usual care and look again in a few days.",
            listOf(
                "Water the soil in the morning, not over the leaves.",
                "Check a few more leaves before you decide the whole field is fine.",
            ),
            listOf("No spray is needed from this photo."),
            listOf(
                "Pull weeds so air can move through the crop.",
                "Do not work in the crop while the leaves are wet.",
            ),
            "Ask for help if new spots, curling, or wilting show up.",
        ),
        hi = copy(
            "यह पत्ती स्वस्थ लगती है। वही देखभाल जारी रखें और कुछ दिन बाद फिर देखें।",
            listOf(
                "सुबह मिट्टी पर पानी दें, पत्तियों के ऊपर नहीं।",
                "पूरे खेत को ठीक मानने से पहले कुछ और पत्तियाँ देखें।",
            ),
            listOf("इस फ़ोटो से छिड़काव की ज़रूरत नहीं है।"),
            listOf(
                "खरपतवार हटाएँ ताकि हवा फसल में चल सके।",
                "पत्तियाँ गीली हों तो फसल में काम न करें।",
            ),
            "नए धब्बे, मुड़ना, या मुरझाना दिखे तो मदद लें।",
        ),
        sw = copy(
            "Jani hili linaonekana kuwa na afya. Endelea na utunzaji wa kawaida na uangalie tena baada ya siku chache.",
            listOf(
                "Mwagilia udongo asubuhi, sio juu ya majani.",
                "Angalia majani mengine kabla ya kuamua shamba lote liko salama.",
            ),
            listOf("Hakuna dawa inayohitajika kutokana na picha hii."),
            listOf(
                "Ondoa magugu ili upepo upite kwenye zao.",
                "Usifanye kazi shambani majani yakiwa na unyevu.",
            ),
            "Omba msaada madoa mapya, kukunja, au kunyauka vikionekana.",
        ),
    )

    private val powdery = pack(
        en = copy(
            "Powdery mildew looks like white dust on the leaf. It spreads when plants are crowded.",
            listOf(
                "Pick off the worst leaves and take them out of the plot.",
                "Give the plants more space if they are touching.",
            ),
            listOf(
                "Water at the base in the morning.",
                "Ask extension before a fungicide, and use only a product labeled for this crop at the label rate.",
            ),
            listOf(
                "Do not wet the leaves late in the day.",
                "Clear old crop remains at the end of the season.",
            ),
            "Ask for help if the white dust covers most leaves or the fruit stops growing.",
        ),
        hi = copy(
            "चूर्णिल आसिता पत्ती पर सफेद धूल जैसी लगती है। पौधे सटे हों तो यह फैलती है।",
            listOf(
                "सबसे खराब पत्तियाँ तोड़कर खेत से बाहर ले जाएँ।",
                "पौधे आपस में छू रहे हों तो उनके बीच जगह दें।",
            ),
            listOf(
                "सुबह जड़ के पास पानी दें।",
                "कवकनाशी से पहले कृषि विभाग से पूछें, और इसी फसल के लिए दर्ज दवा डिब्बे की मात्रा में लें।",
            ),
            listOf(
                "दिन के आखिर में पत्तियाँ न भिगोएँ।",
                "मौसम के अंत में पुराने फसल अवशेष हटा दें।",
            ),
            "सफेद धूल ज़्यादातर पत्तियों पर हो या फल रुक जाएँ तो मदद लें।",
        ),
        sw = copy(
            "Ubwiri wa unga huonekana kama vumbi jeupe kwenye jani. Huenea mimea ikiwa imejaa.",
            listOf(
                "Ondoa majani mabaya zaidi na uyatoe shambani.",
                "Weka nafasi mimea ikigusana.",
            ),
            listOf(
                "Mwagilia shina asubuhi.",
                "Uliza ugani kabla ya dawa ya kuvu, na tumia tu dawa iliyosajiliwa kwa zao hili kwa kiwango cha lebo.",
            ),
            listOf(
                "Usiweke maji kwenye majani jioni.",
                "Ondoa mabaki ya zao mwishoni mwa msimu.",
            ),
            "Omba msaada vumbi jeupe likifunika majani mengi au tunda likiacha kukua.",
        ),
    )

    private val bacterial = pack(
        en = copy(
            "Bacterial spot makes small dark spots. Rain splash and wet tools spread it.",
            listOf(
                "Remove the worst spotted leaves and take them out of the plot.",
                "Do not work among the plants while they are wet.",
            ),
            listOf(
                "Water at the base, not over the leaves.",
                "Ask extension before any spray. Many fungicides do not stop bacteria.",
            ),
            listOf(
                "Save seed only from healthy plants.",
                "Clean tools after you cut a sick plant.",
            ),
            "Ask for help if spots reach the fruit or new leaves keep dying.",
        ),
        hi = copy(
            "जीवाणु धब्बा छोटे काले धब्बे बनाता है। बारिश की छींट और गीले औज़ार इसे फैलाते हैं।",
            listOf(
                "सबसे धब्बेदार पत्तियाँ हटाकर खेत से बाहर ले जाएँ।",
                "पौधे गीले हों तो उनके बीच काम न करें।",
            ),
            listOf(
                "पत्तियों के ऊपर नहीं, जड़ के पास पानी दें।",
                "छिड़काव से पहले कृषि विभाग से पूछें। कई कवकनाशी जीवाणु नहीं रोकते।",
            ),
            listOf(
                "बीज केवल स्वस्थ पौधों से रखें।",
                "बीमार पौधा काटने के बाद औज़ार साफ करें।",
            ),
            "धब्बे फल तक पहुँचें या नई पत्तियाँ मरती रहें तो मदद लें।",
        ),
        sw = copy(
            "Doa la bakteria hufanya madoa madogo meusi. Mruko wa mvua na vifaa vyenye unyevu hueneza.",
            listOf(
                "Ondoa majani yenye madoa mabaya na uyatoe shambani.",
                "Usifanye kazi kati ya mimea ikiwa na unyevu.",
            ),
            listOf(
                "Mwagilia shina, sio juu ya majani.",
                "Uliza ugani kabla ya dawa yoyote. Dawa nyingi za kuvu hazizuii bakteria.",
            ),
            listOf(
                "Hifadhi mbegu kutoka mimea yenye afya pekee.",
                "Safisha vifaa baada ya kukata mmea mgonjwa.",
            ),
            "Omba msaada madoa yakifika kwenye tunda au majani mapya yakizidi kufa.",
        ),
    )

    private val greening = pack(
        en = copy(
            "Citrus greening yellows a leaf unevenly and can leave fruit small and bitter. An insect, the citrus psyllid, spreads it.",
            listOf(
                "Do not move this plant, its cuttings, or its fruit to another orchard.",
                "Mark the tree so it is not mixed with healthy trees.",
            ),
            listOf(
                "No home spray cures a tree once it has greening.",
                "Ask the local extension office to confirm it and say whether the tree should be removed.",
            ),
            listOf(
                "Buy new citrus only from a nursery allowed to sell it.",
                "Check the young flush for psyllids.",
            ),
            "Ask for help as soon as you suspect greening. Do not wait until the whole tree is yellow.",
        ),
        hi = copy(
            "साइट्रस ग्रीनिंग पत्ती को असमान पीला करती है और फल छोटे व कड़वे रह सकते हैं। साइट्रस सिल्ला कीट इसे फैलाता है।",
            listOf(
                "इस पौधे, उसकी कलम, या फल को दूसरे बगीचे में न ले जाएँ।",
                "पेड़ पर निशान लगाएँ ताकि वह स्वस्थ पेड़ों में न मिल जाए।",
            ),
            listOf(
                "एक बार ग्रीनिंग हो जाए तो घर का छिड़काव पेड़ को ठीक नहीं करता।",
                "स्थानीय कृषि विभाग से पुष्टि करवाएँ और पूछें कि पेड़ हटाना है या नहीं।",
            ),
            listOf(
                "नया नींबू-वर्गीय पौधा केवल अधिकृत नर्सरी से लें।",
                "नई कोपलों पर सिल्ला देखें।",
            ),
            "ग्रीनिंग का शक होते ही मदद लें। पूरा पेड़ पीला होने तक इंतज़ार न करें।",
        ),
        sw = copy(
            "Ugonjwa wa kijani kibichi wa machungwa hufanya jani kuwa njano bila mpangilio, na tunda linaweza kubaki dogo na chungu. Psilidi wa machungwa hueneza.",
            listOf(
                "Usihamishe mmea huu, vipandikizi vyake, au tunda lake kwenda bustani nyingine.",
                "Weka alama kwenye mti ili usichanganywe na miti yenye afya.",
            ),
            listOf(
                "Hakuna dawa ya nyumbani inayoponya mti ulioshaugonjwa.",
                "Uliza ofisi ya ugani ithibitishe na ikueleze kama mti unapaswa kung'olewa.",
            ),
            listOf(
                "Nunua michungwa mipya kutoka kitalu kinachoruhusiwa kuiuza.",
                "Angalia machipukizi mapya kwa psilidi.",
            ),
            "Omba msaada mara unaposhuku ugonjwa huu. Usisubiri mti wote uwe njano.",
        ),
    )

    private val esca = pack(
        en = copy(
            "Esca stripes grape leaves between the veins and can dry a whole arm of the vine. It lives in the wood.",
            listOf(
                "Mark the vine. Do not take cuttings from it.",
                "In dry weather, cut out a dead arm and take the wood away.",
            ),
            listOf(
                "No spray cures wood that is already infected.",
                "Ask extension how to prune, and whether the vine should be replaced.",
            ),
            listOf(
                "Prune in dry weather and clean the tool between vines.",
                "Do not leave infected wood in the vineyard.",
            ),
            "Ask for help if a whole arm dies or several vines show the same stripes.",
        ),
        hi = copy(
            "एस्का अंगूर की पत्ती पर नसों के बीच धारियाँ बनाता है और पूरी शाखा सुखा सकता है। यह लकड़ी में रहता है।",
            listOf(
                "बेल पर निशान लगाएँ। उससे कलम न लें।",
                "सूखे मौसम में मरी शाखा काटकर लकड़ी दूर ले जाएँ।",
            ),
            listOf(
                "कोई छिड़काव पहले से संक्रमित लकड़ी को ठीक नहीं करता।",
                "छँटाई कैसे करें और बेल बदलनी है या नहीं, कृषि विभाग से पूछें।",
            ),
            listOf(
                "सूखे मौसम में छँटाई करें और हर बेल के बाद औज़ार साफ करें।",
                "संक्रमित लकड़ी बगीचे में न छोड़ें।",
            ),
            "पूरी शाखा मर जाए या कई बेलों पर वही धारियाँ हों तो मदद लें।",
        ),
        sw = copy(
            "Esca hufanya mistari kwenye jani la zabibu kati ya mishipa na inaweza kukausha tawi zima. Huishi kwenye ubao.",
            listOf(
                "Weka alama kwenye mzabibu. Usichukue vipandikizi kutoka kwake.",
                "Wakati wa ukame, kata tawi lililokufa na uondoe ubao.",
            ),
            listOf(
                "Hakuna dawa inayoponya ubao ulioshaambukizwa.",
                "Uliza ugani jinsi ya kupogoa, na kama mzabibu unapaswa kubadilishwa.",
            ),
            listOf(
                "Pogoa wakati wa ukame na safisha kifaa kati ya mizabibu.",
                "Usiacha ubao ulioambukizwa shambani.",
            ),
            "Omba msaada tawi zima likifa au mizabibu mingi ikionyesha mistari hiyo.",
        ),
    )

    private val blackRot = pack(
        en = copy(
            "Black rot makes brown spots on grape leaves and can dry the berries into hard black mummies.",
            listOf(
                "Pick off dried berries and diseased leaves and take them out of the vineyard.",
                "Do not leave them on the ground under the vine.",
            ),
            listOf(
                "Prune so air moves and the leaves dry after rain.",
                "Ask extension before a fungicide, and only at the label rate for grape.",
            ),
            listOf(
                "Remove dried berries at the end of the season.",
                "Keep the canopy open next year.",
            ),
            "Ask for help if many berries are drying.",
        ),
        hi = copy(
            "काली सड़न अंगूर की पत्तियों पर भूरे धब्बे बनाती है और दानों को सख्त काले सूखे फल में बदल सकती है।",
            listOf(
                "सूखे दाने और बीमार पत्तियाँ तोड़कर बगीचे से बाहर ले जाएँ।",
                "उन्हें बेल के नीचे ज़मीन पर न छोड़ें।",
            ),
            listOf(
                "ऐसी छँटाई करें कि हवा चले और बारिश के बाद पत्तियाँ सूखें।",
                "कवकनाशी से पहले कृषि विभाग से पूछें, और अंगूर के लिए डिब्बे की मात्रा ही लें।",
            ),
            listOf(
                "मौसम के अंत में सूखे दाने हटा दें।",
                "अगले साल पत्तियों के बीच हवा रहने दें।",
            ),
            "कई दाने सूख रहे हों तो मदद लें।",
        ),
        sw = copy(
            "Uozo mweusi hufanya madoa ya kahawia kwenye majani ya zabibu na unaweza kukausha matunda kuwa magumu meusi.",
            listOf(
                "Ondoa matunda yaliyokauka na majani yenye ugonjwa na uyatoe shambani.",
                "Usiyaache ardhini chini ya mzabibu.",
            ),
            listOf(
                "Pogoa ili upepo upite na majani yakauke baada ya mvua.",
                "Uliza ugani kabla ya dawa ya kuvu, na tu kwa kiwango cha lebo kwa zabibu.",
            ),
            listOf(
                "Ondoa matunda yaliyokauka mwishoni mwa msimu.",
                "Weka mwavuli wazi mwaka ujao.",
            ),
            "Omba msaada matunda mengi yakikauka.",
        ),
    )

    private val leafBlight = pack(
        en = copy(
            "Grapevine leaf blight makes brown patches that can dry the leaf from the edge.",
            listOf(
                "Remove the worst leaves and take them out of the vineyard.",
                "Open the canopy so leaves dry after rain.",
            ),
            listOf(
                "Water the soil, not the leaves, in the morning.",
                "Ask extension before a fungicide, and only at the label rate for grape.",
            ),
            listOf(
                "Clear fallen leaves at the end of the season.",
                "Keep vines off the ground where you can.",
            ),
            "Ask for help if the patches cover most leaves before harvest.",
        ),
        hi = copy(
            "अंगूर की पत्ती झुलसा किनारे से पत्ती सुखाने वाले भूरे धब्बे बनाती है।",
            listOf(
                "सबसे खराब पत्तियाँ हटाकर बगीचे से बाहर ले जाएँ।",
                "पत्तियाँ खोलें ताकि बारिश के बाद वे सूखें।",
            ),
            listOf(
                "सुबह मिट्टी पर पानी दें, पत्तियों पर नहीं।",
                "कवकनाशी से पहले कृषि विभाग से पूछें, और अंगूर के लिए डिब्बे की मात्रा ही लें।",
            ),
            listOf(
                "मौसम के अंत में गिरी पत्तियाँ हटा दें।",
                "जहाँ हो सके बेलें ज़मीन से ऊपर रखें।",
            ),
            "तुड़ाई से पहले धब्बे ज़्यादातर पत्तियों पर हों तो मदद लें।",
        ),
        sw = copy(
            "Unyauka wa jani la zabibu hufanya mabaka ya kahawia yanayoweza kukausha jani kuanzia ukingo.",
            listOf(
                "Ondoa majani mabaya na uyatoe shambani.",
                "Fungua mwavuli ili majani yakauke baada ya mvua.",
            ),
            listOf(
                "Mwagilia udongo asubuhi, sio majani.",
                "Uliza ugani kabla ya dawa ya kuvu, na tu kwa kiwango cha lebo kwa zabibu.",
            ),
            listOf(
                "Ondoa majani yaliyoanguka mwishoni mwa msimu.",
                "Weka mizabibu juu ya ardhi unapoweza.",
            ),
            "Omba msaada mabaka yakifunika majani mengi kabla ya kuvuna.",
        ),
    )

    private val scorch = pack(
        en = copy(
            "Leaf scorch makes purple to brown patches on strawberry leaves, and the leaf can dry from the edge.",
            listOf(
                "Clip the worst leaves and take them out of the bed.",
                "Do not water over the top late in the day.",
            ),
            listOf(
                "Water at the base in the morning and keep the bed weeded.",
                "Ask extension before a fungicide, and only at the label rate for strawberry.",
            ),
            listOf(
                "Start the next bed with healthy plants.",
                "Leave space so air can move.",
            ),
            "Ask for help if most leaves are drying or the fruit is rotting.",
        ),
        hi = copy(
            "पत्ती झुलसन स्ट्रॉबेरी पर बैंगनी से भूरे धब्बे बनाती है, और पत्ती किनारे से सूख सकती है।",
            listOf(
                "सबसे खराब पत्तियाँ काटकर क्यारी से बाहर ले जाएँ।",
                "दिन के आखिर में ऊपर से पानी न दें।",
            ),
            listOf(
                "सुबह जड़ के पास पानी दें और क्यारी निराई रखें।",
                "कवकनाशी से पहले कृषि विभाग से पूछें, और स्ट्रॉबेरी के लिए डिब्बे की मात्रा ही लें।",
            ),
            listOf(
                "अगली क्यारी स्वस्थ पौधों से शुरू करें।",
                "हवा चलने जितनी जगह छोड़ें।",
            ),
            "ज़्यादातर पत्तियाँ सूखें या फल सड़ें तो मदद लें।",
        ),
        sw = copy(
            "Kuungua kwa jani hufanya mabaka ya zambarau hadi kahawia kwenye majani ya strawberry, na jani linaweza kukauka kuanzia ukingo.",
            listOf(
                "Kata majani mabaya na uyatoe kitaluni.",
                "Usimwagilie juu jioni.",
            ),
            listOf(
                "Mwagilia shina asubuhi na palilia kitanda.",
                "Uliza ugani kabla ya dawa ya kuvu, na tu kwa kiwango cha lebo kwa strawberry.",
            ),
            listOf(
                "Anza kitanda kijacho na mimea yenye afya.",
                "Acha nafasi ili upepo upite.",
            ),
            "Omba msaada majani mengi yakikauka au tunda likioza.",
        ),
    )

    private val problem = pack(
        en = copy(
            "The leaf model found a problem on this leaf. Start with the worst leaves, and ask extension before you spray.",
            listOf(
                "Remove the worst leaves and take them out of the plot.",
                "Do not work in the crop while the leaves are wet.",
            ),
            listOf(
                "Water at the base in the morning so the leaves can dry.",
                "Ask extension before any spray, and use only a product labeled for this crop at the label rate.",
            ),
            listOf(
                "Keep plants spaced so air moves.",
                "Clear old crop remains at the end of the season.",
            ),
            "Ask for help if it spreads to most plants or the fruit is damaged.",
        ),
        hi = copy(
            "पत्ती मॉडल को इस पत्ती पर समस्या दिखी। पहले सबसे खराब पत्तियों से शुरू करें, और छिड़काव से पहले कृषि विभाग से पूछें।",
            listOf(
                "सबसे खराब पत्तियाँ हटाकर खेत से बाहर ले जाएँ।",
                "पत्तियाँ गीली हों तो फसल में काम न करें।",
            ),
            listOf(
                "सुबह जड़ के पास पानी दें ताकि पत्तियाँ सूख सकें।",
                "किसी भी छिड़काव से पहले कृषि विभाग से पूछें, और इसी फसल के लिए दर्ज दवा डिब्बे की मात्रा में लें।",
            ),
            listOf(
                "पौधों के बीच हवा रहने दें।",
                "मौसम के अंत में पुराने फसल अवशेष हटा दें।",
            ),
            "यह ज़्यादातर पौधों तक फैले या फल खराब हो तो मदद लें।",
        ),
        sw = copy(
            "Modeli ya jani imepata tatizo kwenye jani hili. Anza na majani mabaya, na uliza ugani kabla ya kunyunyizia.",
            listOf(
                "Ondoa majani mabaya na uyatoe shambani.",
                "Usifanye kazi shambani majani yakiwa na unyevu.",
            ),
            listOf(
                "Mwagilia shina asubuhi ili majani yaweze kukauka.",
                "Uliza ugani kabla ya dawa yoyote, na tumia tu dawa iliyosajiliwa kwa zao hili kwa kiwango cha lebo.",
            ),
            listOf(
                "Weka mimea na nafasi ili upepo upite.",
                "Ondoa mabaki ya zao mwishoni mwa msimu.",
            ),
            "Omba msaada likienea kwa mimea mingi au tunda likiharibika.",
        ),
    )

    private fun pack(en: CareCopy, hi: CareCopy, sw: CareCopy): LangPack = LangPack(en, hi, sw)

    private fun copy(
        summary: String,
        now: List<String>,
        treatment: List<String>,
        prevention: List<String>,
        help: String,
    ): CareCopy = CareCopy(summary, now, treatment, prevention, help)

    private data class CareCopy(
        val summary: String,
        val now: List<String>,
        val treatment: List<String>,
        val prevention: List<String>,
        val help: String,
    ) {
        fun toAdvice(): AdviceText = AdviceText(summary, now, treatment, prevention, help)
    }

    private data class LangPack(val en: CareCopy, val hi: CareCopy, val sw: CareCopy) {
        fun inLanguage(language: String): AdviceText {
            val copy = when (language) {
                "hi" -> hi
                "sw" -> sw
                else -> en
            }
            return copy.toAdvice()
        }
    }
}
