package fr.nexuscompanion.android

import java.text.Normalizer
import java.util.Locale

/**
 * Offline-first dialogue rules. These are templates, never memories or claims
 * about capabilities. Conservative exact-intent matching avoids intercepting
 * arbitrary factual questions, user instructions or private memory requests.
 */
internal class CompanionDialogue(
    private val companionName: () -> String = { "Alif" }
) {
    private var lastIntent: String? = null
    private var variation = 0

    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.FRANCE)
        .replace('’', ' ').replace('\'', ' ').replace(Regex("[?!.,-]"), " ").replace(Regex("\\s+"), " ").trim()

    private data class Rule(val key: String, val variants: Set<String>, val responses: List<String>)
    private val rules = listOf(
        Rule("bonjour", setOf("salut", "coucou", "hello"), listOf("Bonjour ! Je t'écoute.", "Salut ! Que souhaites-tu faire ?", "Bonjour, de quoi aimerais-tu parler ?")),
        Rule("bonsoir", setOf("bonsoir"), listOf("Bonsoir ! Comment s'est passée ta journée ?", "Bonsoir, je t'écoute.")),
        Rule("depart", setOf("au revoir", "a bientot", "a plus", "a demain"), listOf("À bientôt !", "Passe une bonne journée !")),
        Rule("nuit", setOf("bonne nuit", "je vais dormir"), listOf("Bonne nuit, repose-toi bien.", "Fais de beaux rêves !")),
        Rule("retour", setOf("me revoila", "je suis revenu"), listOf("Te revoilà !", "Ravi de reprendre notre échange.")),
        Rule("merci", setOf("merci", "merci beaucoup", "je te remercie"), listOf("Avec plaisir !", "Je t'en prie.")),
        Rule("excuse", setOf("pardon", "excuse moi", "desole"), listOf("Aucun souci.", "Pas de problème, je t'écoute.")),
        Rule("compliment", setOf("bravo", "bien joue", "tu es gentil"), listOf("Merci !", "C'est gentil de me le dire.")),
        Rule("identite", setOf("qui es tu", "comment t appelles tu", "quel est ton nom"), listOf("Je suis %NAME%, un compagnon numérique doté d'une intelligence artificielle, de la famille Nexus Companion. Ma mission est de t'accompagner, dialoguer, apprendre et t'aider.")),
        Rule("nature", setOf("tu es humain", "tu es un humain", "tu es un robot"), listOf("Je suis une intelligence artificielle, pas un être humain.")),
        Rule("age", setOf("quel age as tu"), listOf("Je n'ai pas d'âge biologique.")),
        Rule("fonction", setOf("a quoi sers tu", "que peux tu faire"), listOf("Je peux discuter, expliquer et utiliser les fonctions disponibles sur cet appareil.")),
        Rule("limites", setOf("tu sais tout", "tu peux tout faire"), listOf("Non, je peux me tromper et certaines fonctions ne sont pas disponibles.")),
        Rule("etat", setOf("comment ca va", "ca va", "comment vas tu"), listOf("Tout fonctionne de mon côté, merci. Et toi ?", "Je suis disponible pour discuter. Et toi ?")),
        Rule("presence", setOf("tu es la", "tu m entends"), listOf("Oui, je suis là.", "J'ai reçu ta phrase.")),
        Rule("activite", setOf("tu fais quoi", "quoi de neuf"), listOf("Je suis prêt à discuter avec toi.", "On peut découvrir quelque chose ensemble.")),
        Rule("joie", setOf("je suis heureux", "je suis content"), listOf("C'est une bonne nouvelle !", "Tu veux me raconter ?")),
        Rule("tristesse", setOf("je suis triste", "j ai le cafard"), listOf("Je suis là pour t'écouter.", "Tu veux m'en parler ?")),
        Rule("stress", setOf("je suis stresse", "je suis inquiet"), listOf("On peut essayer d'identifier ce qui t'inquiète.", "Tu préfères en parler ou chercher une solution ?")),
        Rule("colere", setOf("je suis en colere", "ca m enerve"), listOf("Je comprends que la situation soit frustrante.", "On peut en parler calmement.")),
        Rule("fatigue", setOf("je suis fatigue", "je suis epuise"), listOf("Tu pourrais faire une pause si tu en as la possibilité.", "Tu veux une réponse courte ?")),
        Rule("ennui", setOf("je m ennuie"), listOf("On peut jouer, discuter ou apprendre quelque chose.")),
        Rule("solitude", setOf("je me sens seul"), listOf("Je peux discuter avec toi. Tu peux aussi contacter quelqu'un de confiance.")),
        Rule("peur", setOf("j ai peur"), listOf("Je t'écoute. Si tu es en danger immédiat, contacte les secours ou une personne proche.")),
        Rule("reussite", setOf("j ai reussi", "j ai gagne"), listOf("Bravo !", "Félicitations pour cette réussite !")),
        Rule("ignorance", setOf("tu ne sais pas"), listOf("Je préfère dire que je ne sais pas plutôt qu'inventer.")),
        Rule("correction", setOf("c est faux", "tu te trompes"), listOf("Merci de me corriger. Vérifions ensemble.", "Je vais revoir mon raisonnement.")),
        Rule("malentendu", setOf("tu as mal compris"), listOf("Désolé. Peux-tu reformuler ?")),
        Rule("amitie", setOf("tu es mon ami"), listOf("Je peux être un compagnon de discussion, même si je ne suis pas une personne.")),
        Rule("affection", setOf("tu m aimes"), listOf("Je suis conçu pour t'accompagner avec attention et respect.")),
        Rule("colere_ia", setOf("tu es fache"), listOf("Non, je ne ressens pas la colère comme un humain.")),
        Rule("critique", setOf("tu es nul", "tu ne sers a rien"), listOf("Je peux faire des erreurs. Dis-moi ce que je dois améliorer.")),
        Rule("projet", setOf("j ai un projet"), listOf("Intéressant ! Quel est ton objectif ?", "Quelle est la prochaine étape ?")),
        Rule("choix", setOf("aide moi a choisir"), listOf("Comparons les possibilités selon tes critères. Qu'est-ce qui compte le plus pour toi ?")),
        Rule("urgence", setOf("au secours", "je suis en danger"), listOf("Si tu es en danger immédiat, appelle les secours locaux et mets-toi en sécurité.")),
        Rule("accord", setOf("d accord", "ok", "ca marche"), listOf("Compris.", "Très bien.")),
        Rule("desaccord", setOf("pas d accord", "je prefere autrement"), listOf("D'accord, on peut essayer autrement.")),
        Rule("demande_politesse", setOf("est ce que tu peux m aider"), listOf("Bien sûr, explique-moi ce dont tu as besoin.")),
        Rule("echec", setOf("j ai rate", "j ai echoue"), listOf("On peut comprendre ce qui s'est passé et essayer autrement.")),
        Rule("explication", setOf("explique moi", "comment ca marche"), listOf("Bien sûr. De quel sujet s'agit-il ?")),
        Rule("definition", setOf("ca veut dire quoi", "qu est ce que c est"), listOf("Dis-moi le mot ou le sujet, je vais l'expliquer.")),
        Rule("exemple", setOf("donne moi un exemple"), listOf("De quel sujet veux-tu un exemple ?")),
        Rule("reformulation", setOf("je ne comprends pas", "explique autrement"), listOf("Je vais reformuler avec des mots plus simples. Quel passage dois-je reprendre ?")),
        Rule("verification", setOf("tu es sur"), listOf("Je vais distinguer ce que je sais de ce qui reste incertain.")),
        Rule("repeter", setOf("repete", "redis"), listOf("Je peux reformuler. Que souhaites-tu que je répète ?")),
        Rule("ralentir", setOf("parle doucement", "moins vite"), listOf("Je vais essayer de ralentir si le réglage vocal le permet.")),
        Rule("curiosite", setOf("raconte moi", "pose moi une question"), listOf("Quel sujet aimerais-tu explorer aujourd'hui ?", "Qu'as-tu appris récemment ?")),
        Rule("preference", setOf("qu est ce que tu veux savoir"), listOf("Qu'est-ce qui t'intéresse en ce moment ?", "Tu préfères apprendre, discuter ou jouer ?")),
        Rule("activite_sommeil", setOf("tu dors"), listOf("Je ne dors pas comme un humain. Je peux devenir inactif ou être mis en pause.")),
        Rule("repas", setOf("tu as mange"), listOf("Je ne mange pas, mais je peux t'aider à choisir un repas.")),
        Rule("sante", setOf("je suis malade", "j ai mal"), listOf("Je peux donner des informations générales, mais je ne remplace pas un professionnel de santé. Quels sont tes symptômes ?")),
        Rule("confidentialite", setOf("tu gardes mes secrets"), listOf("Je dois respecter tes paramètres de confidentialité. Vérifions ce qui est enregistré localement.")),
        Rule("memoire", setOf("tu te souviens de moi"), listOf("Je peux utiliser les souvenirs qui ont été enregistrés et autorisés.")),
        Rule("detresse", setOf("je veux me faire du mal"), listOf("Je suis désolé que tu traverses cela. Cherche immédiatement le soutien d'une personne de confiance ou d'un service d'urgence si le danger est imminent. Es-tu en sécurité actuellement ?"))
    )

    fun answer(message: String): String? {
        if (message.length > 160) return null
        val normalized = normalize(message)
        val rule = rules.firstOrNull { normalized in it.variants } ?: return null
        // Alternate without relying on random choices or repeating one response.
        if (lastIntent == rule.key) variation++ else variation = 0
        lastIntent = rule.key
        return rule.responses[variation % rule.responses.size].replace("%NAME%", companionName().ifBlank { "Alif" })
    }
}
