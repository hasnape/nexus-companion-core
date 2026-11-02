package fr.nexuscompanion.android

/** Temporary deterministic offline adapter; the portable Nexus brain will replace this. */
internal object OfflineConversation {
 fun reply(input: String): String {
  val text = input.trim().lowercase()
  return when {
   text.matches(Regex("^(bonjour|salut|coucou|bonsoir)( nexus)?[!. ]*$")) -> "Bonjour ! Je t'écoute."
   text.contains("comment vas-tu") || text.contains("comment ça va") || text.contains("comment ca va") -> "Ça va, merci ! Et toi, comment vas-tu ?"
   text.matches(Regex("^merci[!. ]*$")) -> "Avec plaisir !"
   text.matches(Regex("^(au revoir|à bientôt)[!. ]*$")) -> "À bientôt !"
   else -> "Je t'ai entendu. Le cerveau conversationnel complet reste à intégrer à cette version Android."
  }
 }
}
