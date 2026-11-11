import { normalizeWakePhrase, stripWakePrefix } from './wake';

// Adapted from Nexus Support Hub's issue taxonomy and relevance routing.
const topics = [
  {
    label: 'la connexion Wi-Fi',
    anchors: ['wifi', 'internet', 'reseau sans fil'],
    clues: ['pas internet', 'pas d internet', 'connecte', 'connexion', 'coupure', 'ne marche plus', 'ne fonctionne plus', 'panne'],
    reply: 'Vérifie si un autre appareil peut ouvrir un site sur le même Wi-Fi. Si lui aussi échoue, regarde l’état de la box. Si seul ton appareil est touché, désactive puis réactive son Wi-Fi et reconnecte-le. Le problème touche combien d’appareils ?'
  },
  {
    label: 'l’imprimante',
    anchors: ['imprimante', 'impression', 'imprimer', 'imprime'],
    clues: ['imprime pas', 'imprime plus', 'rien ne sort', 'hors ligne', 'file d attente', 'bourrage', 'bloquee', 'erreur'],
    reply: 'Vérifie que l’imprimante est prête et sélectionnée sur ton appareil. Ouvre sa file d’attente, puis essaie une page de test depuis l’imprimante. Que vois-tu sur son écran ou dans la file d’attente ?'
  },
  {
    label: 'la messagerie Outlook',
    anchors: ['outlook', 'messagerie', 'boite mail'],
    clues: ['synchronise plus', 'recois plus', 'recoit plus', 'envoi', 'reception', 'hors connexion', 'ne fonctionne plus', 'erreur'],
    reply: 'Essaie d’ouvrir ta messagerie dans ton navigateur. Si les messages y arrivent, vérifie si Outlook affiche « Travailler hors connexion » et regarde la boîte d’envoi. Le souci concerne l’envoi, la réception ou les deux ?'
  },
  {
    label: 'la lenteur de l’ordinateur',
    anchors: ['ordinateur', 'pc', 'windows'],
    clues: ['lent', 'rame', 'lenteur', 'disque plein', 'fige', 'espace disque'],
    reply: 'Sous Windows, ouvre le Gestionnaire des tâches avec Ctrl + Maj + Échap. Regarde dans Processus si le processeur, la mémoire ou le disque reste saturé, puis vérifie l’espace libre dans Paramètres, Système, Stockage. Quel indicateur monte le plus ?'
  },
  {
    label: 'le VPN',
    anchors: ['vpn', 'tunnel vpn', 'acces distant'],
    clues: ['connexion', 'connecter', 'echoue', 'erreur', 'ne marche plus', 'ne fonctionne plus', 'bloque'],
    reply: 'Vérifie d’abord que tu peux ouvrir un site avec le VPN déconnecté. Relance ensuite la connexion avec ton client VPN habituel et lis le message affiché. Quel est son code d’erreur ? Évite de partager ton mot de passe ou un code de validation.'
  }
];

export const renderLocalSupportReply = (message: string): string | null => {
  const query = normalizeWakePhrase(stripWakePrefix(message, { allowFullNameWake: true })).replace(/\bwi fi\b/g, 'wifi');
  const phrase = ` ${query} `;
  const hits = (signals: string[]) => signals.filter((signal) => phrase.includes(` ${signal} `)).length;
  const troubleshooting = /\b(?:depanne|depannage|probleme|panne|reparer|ne marche pas|ne marche plus|ne fonctionne pas|ne fonctionne plus)\b/.test(query);
  const ranked = topics.flatMap((topic) => {
    const anchors = hits(topic.anchors);
    const clues = hits(topic.clues);
    return anchors && (clues || troubleshooting) ? [{ topic, score: 3 * anchors + 2 * clues }] : [];
  }).sort((left, right) => right.score - left.score);

  const [best, next] = ranked;
  if (!best) return null;
  if (next && best.score - next.score < 2) {
    return `Tu veux qu’on regarde d’abord ${best.topic.label} ou ${next.topic.label} ?`;
  }
  return best.topic.reply;
};
