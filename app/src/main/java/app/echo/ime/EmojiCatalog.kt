package app.echo.ime

data class EmojiEntry(val value: String,val category: Int,val tags: String)
object EmojiCatalog {
    val entries=listOf(
        EmojiEntry("😀",0,"开心 高兴 笑脸 happy smile"),EmojiEntry("😄",0,"大笑 开心 happy laugh"),EmojiEntry("😁",0,"笑 开心 grin"),EmojiEntry("😂",0,"笑哭 大笑 laugh tears"),EmojiEntry("🤣",0,"笑哭 大笑 rolling laugh"),
        EmojiEntry("😊",0,"微笑 开心 smile blush"),EmojiEntry("😍",0,"喜欢 爱 心 love heart eyes"),EmojiEntry("🥰",0,"爱 喜欢 love heart"),EmojiEntry("😘",0,"亲亲 亲吻 kiss love"),EmojiEntry("😎",0,"酷 墨镜 cool"),EmojiEntry("🤔",0,"思考 想 think"),
        EmojiEntry("😭",0,"哭 难过 伤心 sad cry"),EmojiEntry("🥹",0,"感动 眼泪 tears"),EmojiEntry("😅",0,"尴尬 汗 sweat"),EmojiEntry("😴",0,"睡觉 困 sleep"),EmojiEntry("😮",0,"惊讶 surprise"),EmojiEntry("🙃",0,"倒脸 upside"),EmojiEntry("😤",0,"生气 愤怒 angry"),EmojiEntry("🥳",0,"庆祝 生日 party birthday"),EmojiEntry("🤗",0,"拥抱 hug"),
        EmojiEntry("👍",1,"赞 棒 好 点赞 good thumb"),EmojiEntry("👎",1,"不好 差 bad thumb down"),EmojiEntry("👌",1,"好的 ok"),EmojiEntry("✌️",1,"胜利 耶 victory"),EmojiEntry("🤞",1,"好运 luck"),EmojiEntry("🤝",1,"握手 合作 handshake"),EmojiEntry("👏",1,"鼓掌 掌声 clap"),EmojiEntry("🙏",1,"谢谢 感谢 拜托 thanks pray"),EmojiEntry("💪",1,"加油 努力 strong"),EmojiEntry("👋",1,"你好 再见 挥手 hello bye wave"),EmojiEntry("🫶",1,"爱 比心 heart love"),EmojiEntry("🙌",1,"举手 庆祝 celebrate"),
        EmojiEntry("❤️",2,"爱 心 红心 love heart"),EmojiEntry("💔",2,"心碎 伤心 broken heart sad"),EmojiEntry("🎉",2,"庆祝 生日 party"),EmojiEntry("✨",2,"星星 闪亮 star sparkle"),EmojiEntry("🎂",2,"蛋糕 生日 birthday cake"),EmojiEntry("🌹",2,"花 玫瑰 rose flower"),EmojiEntry("☀️",2,"太阳 天气 晴天 sun weather"),EmojiEntry("🌧️",2,"下雨 天气 rain weather"),EmojiEntry("🐶",2,"狗 dog animal"),EmojiEntry("🐱",2,"猫 cat animal"),EmojiEntry("🍎",2,"苹果 apple food"),EmojiEntry("☕",2,"咖啡 coffee drink"),
        EmojiEntry("(๑•̀ㅂ•́)و✧",3,"加油 努力"),EmojiEntry("(≧▽≦)",3,"开心 大笑"),EmojiEntry("(╥﹏╥)",3,"难过 哭"),EmojiEntry("(•̀ᴗ•́)و",3,"加油"),EmojiEntry("(´･ω･`)",3,"可爱"),EmojiEntry("(づ｡◕‿‿◕｡)づ",3,"拥抱"),EmojiEntry("(￣▽￣)",3,"开心"),EmojiEntry("(ง •̀_•́)ง",3,"努力 加油")
    )
    fun search(query: String): List<String> {
        val words=query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if(words.isEmpty()) return entries.map { it.value }
        return entries.filter { entry -> words.all { entry.tags.contains(it,true) || entry.value.contains(it) } }.map { it.value }
    }
}
