package com.example.ui.components

data class AvatarItem(
    val name: String,
    val url: String
)

object AvatarData {
    val avatars = listOf(
        // Dragon Ball Series
        AvatarItem("Kid Buu", "https://cdn.myanimelist.net/images/characters/4/299532.jpg"),
        AvatarItem("Goku", "https://cdn.myanimelist.net/images/characters/9/496225.jpg"),
        AvatarItem("Vegeta", "https://cdn.myanimelist.net/images/characters/15/283935.jpg"),
        AvatarItem("Beerus", "https://cdn.myanimelist.net/images/characters/13/215457.jpg"),

        // One Piece Series
        AvatarItem("Luffy", "https://cdn.myanimelist.net/images/characters/9/310307.jpg"),
        AvatarItem("Zoro", "https://cdn.myanimelist.net/images/characters/3/100534.jpg"),
        AvatarItem("Sanji", "https://cdn.myanimelist.net/images/characters/5/136769.jpg"),
        AvatarItem("Chopper", "https://cdn.myanimelist.net/images/characters/3/263263.jpg"),
        AvatarItem("Nami", "https://cdn.myanimelist.net/images/characters/2/263249.jpg"),
        AvatarItem("Nico Robin", "https://cdn.myanimelist.net/images/characters/4/263265.jpg"),
        AvatarItem("Usopp", "https://cdn.myanimelist.net/images/characters/11/263255.jpg"),
        AvatarItem("Ace", "https://cdn.myanimelist.net/images/characters/13/263253.jpg"),

        // Naruto Series
        AvatarItem("Naruto", "https://cdn.myanimelist.net/images/characters/9/131317.jpg"),
        AvatarItem("Sasuke", "https://cdn.myanimelist.net/images/characters/15/283937.jpg"),
        AvatarItem("Kakashi", "https://cdn.myanimelist.net/images/characters/7/284129.jpg"),
        AvatarItem("Itachi", "https://cdn.myanimelist.net/images/characters/13/283944.jpg"),
        AvatarItem("Gaara", "https://cdn.myanimelist.net/images/characters/12/283946.jpg"),
        AvatarItem("Jiraiya", "https://cdn.myanimelist.net/images/characters/12/323315.jpg"),
        AvatarItem("Orochimaru", "https://cdn.myanimelist.net/images/characters/2/310321.jpg"),
        AvatarItem("Hinata", "https://cdn.myanimelist.net/images/characters/7/283945.jpg"),

        // Jujutsu Kaisen Series
        AvatarItem("Gojo", "https://cdn.myanimelist.net/images/characters/16/420791.jpg"),
        AvatarItem("Yuji Itadori", "https://cdn.myanimelist.net/images/characters/16/420790.jpg"),
        AvatarItem("Megumi", "https://cdn.myanimelist.net/images/characters/14/420792.jpg"),
        AvatarItem("Nobara", "https://cdn.myanimelist.net/images/characters/2/420793.jpg"),
        AvatarItem("Nanami", "https://cdn.myanimelist.net/images/characters/11/420795.jpg"),
        AvatarItem("Sukuna", "https://cdn.myanimelist.net/images/characters/2/420794.jpg"),

        // Demon Slayer Series
        AvatarItem("Tanjirou", "https://cdn.myanimelist.net/images/characters/4/386047.jpg"),
        AvatarItem("Nezuko", "https://cdn.myanimelist.net/images/characters/3/386053.jpg"),
        AvatarItem("Inosuke", "https://cdn.myanimelist.net/images/characters/3/386051.jpg"),
        AvatarItem("Zenitsu", "https://cdn.myanimelist.net/images/characters/13/386049.jpg"),
        AvatarItem("Giyu", "https://cdn.myanimelist.net/images/characters/12/386055.jpg"),
        AvatarItem("Rengoku", "https://cdn.myanimelist.net/images/characters/10/414522.jpg"),
        AvatarItem("Muzan", "https://cdn.myanimelist.net/images/characters/13/391035.jpg"),
        AvatarItem("Nezuko Bamboo", "https://cdn.myanimelist.net/images/characters/8/386054.jpg"),

        // Spy x Family Series
        AvatarItem("Anya", "https://cdn.myanimelist.net/images/characters/16/473722.jpg"),
        AvatarItem("Loid", "https://cdn.myanimelist.net/images/characters/2/473721.jpg"),
        AvatarItem("Yor", "https://cdn.myanimelist.net/images/characters/10/473723.jpg"),
        AvatarItem("Bond Dog", "https://cdn.myanimelist.net/images/characters/11/484399.jpg"),

        // Attack on Titan Series
        AvatarItem("Eren", "https://cdn.myanimelist.net/images/characters/10/216849.jpg"),
        AvatarItem("Mikasa", "https://cdn.myanimelist.net/images/characters/9/215625.jpg"),
        AvatarItem("Armin", "https://cdn.myanimelist.net/images/characters/16/216857.jpg"),
        AvatarItem("Levi", "https://cdn.myanimelist.net/images/characters/2/241417.jpg"),
        AvatarItem("Erwin", "https://cdn.myanimelist.net/images/characters/5/216859.jpg"),
        AvatarItem("Hange", "https://cdn.myanimelist.net/images/characters/16/216861.jpg"),

        // One Punch Man Series
        AvatarItem("Saitama", "https://cdn.myanimelist.net/images/characters/11/294318.jpg"),
        AvatarItem("Saitama Serious", "https://cdn.myanimelist.net/images/characters/14/287373.jpg"),
        AvatarItem("Genos", "https://cdn.myanimelist.net/images/characters/3/294320.jpg"),
        AvatarItem("King", "https://cdn.myanimelist.net/images/characters/2/301540.jpg"),
        AvatarItem("Tatsumaki", "https://cdn.myanimelist.net/images/characters/7/294322.jpg"),

        // Mob Psycho 100 Series
        AvatarItem("Mob", "https://cdn.myanimelist.net/images/characters/7/310343.jpg"),
        AvatarItem("Reigen", "https://cdn.myanimelist.net/images/characters/5/314068.jpg"),
        AvatarItem("Dimple", "https://cdn.myanimelist.net/images/characters/14/314070.jpg"),

        // Hunter x Hunter Series
        AvatarItem("Gon", "https://cdn.myanimelist.net/images/characters/11/127325.jpg"),
        AvatarItem("Killua", "https://cdn.myanimelist.net/images/characters/2/127329.jpg"),
        AvatarItem("Kurapika", "https://cdn.myanimelist.net/images/characters/13/127327.jpg"),
        AvatarItem("Hisoka", "https://cdn.myanimelist.net/images/characters/2/153209.jpg"),
        AvatarItem("Chrollo", "https://cdn.myanimelist.net/images/characters/14/153215.jpg"),

        // Chainsaw Man Series
        AvatarItem("Pochita", "https://cdn.myanimelist.net/images/characters/11/486244.jpg"),
        AvatarItem("Denji", "https://cdn.myanimelist.net/images/characters/4/486245.jpg"),
        AvatarItem("Power", "https://cdn.myanimelist.net/images/characters/14/486246.jpg"),
        AvatarItem("Makima", "https://cdn.myanimelist.net/images/characters/14/486247.jpg"),
        AvatarItem("Aki", "https://cdn.myanimelist.net/images/characters/10/486248.jpg"),

        // Death Note Series
        AvatarItem("Ryuk", "https://cdn.myanimelist.net/images/characters/2/31429.jpg"),
        AvatarItem("Light", "https://cdn.myanimelist.net/images/characters/6/122340.jpg"),
        AvatarItem("L", "https://cdn.myanimelist.net/images/characters/10/31427.jpg"),
        AvatarItem("Misa", "https://cdn.myanimelist.net/images/characters/5/31431.jpg"),

        // Fullmetal Alchemist Series
        AvatarItem("Edward", "https://cdn.myanimelist.net/images/characters/9/72533.jpg"),
        AvatarItem("Alphonse", "https://cdn.myanimelist.net/images/characters/5/72535.jpg"),
        AvatarItem("Roy Mustang", "https://cdn.myanimelist.net/images/characters/11/72537.jpg"),

        // Gintama Series
        AvatarItem("Gintoki", "https://cdn.myanimelist.net/images/characters/4/501255.jpg"),
        AvatarItem("Kagura", "https://cdn.myanimelist.net/images/characters/13/263251.jpg"),
        AvatarItem("Shinpachi", "https://cdn.myanimelist.net/images/characters/5/121768.jpg"),
        AvatarItem("Elizabeth", "https://cdn.myanimelist.net/images/characters/16/121774.jpg"),

        // JoJo's Bizarre Adventure Series
        AvatarItem("Jotaro", "https://cdn.myanimelist.net/images/characters/13/253011.jpg"),
        AvatarItem("Dio", "https://cdn.myanimelist.net/images/characters/3/190892.jpg"),
        AvatarItem("Joseph Joestar", "https://cdn.myanimelist.net/images/characters/13/193014.jpg"),
        AvatarItem("Giorno", "https://cdn.myanimelist.net/images/characters/5/358487.jpg"),

        // Other Iconic Series
        AvatarItem("Pikachu", "https://cdn.myanimelist.net/images/characters/10/215627.jpg"),
        AvatarItem("Ash Ketchum", "https://cdn.myanimelist.net/images/characters/12/323330.jpg"),
        AvatarItem("Rimuru", "https://cdn.myanimelist.net/images/characters/6/367373.jpg"),
        AvatarItem("Marin Kitagawa", "https://cdn.myanimelist.net/images/characters/15/462153.jpg"),
        AvatarItem("Kaguya", "https://cdn.myanimelist.net/images/characters/16/370960.jpg"),
        AvatarItem("Chika", "https://cdn.myanimelist.net/images/characters/7/370962.jpg")
    )

    fun getDefaultAvatar(): String {
        return avatars.first().url
    }
}
